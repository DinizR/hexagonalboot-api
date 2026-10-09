package systems.porto.api.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import systems.porto.adapter.Adapter;
import systems.porto.adapter.client.ClientAdapter;
import systems.porto.adapter.entry.AbstractEntryAdapter;
import systems.porto.adapter.entry.EntryAdapter;
import systems.porto.api.auth.AccessTokenService;
import systems.porto.api.auth.AuthenticatedPrincipal;
import systems.porto.api.auth.IdentityProvider;
import systems.porto.api.auth.SecretHasher;
import systems.porto.api.config.PortoApiProperties;
import systems.porto.api.context.ApiContext;
import systems.porto.api.context.ApiContextConstants;
import systems.porto.api.context.ApiContextFactory;
import systems.porto.api.context.RegistryInvocation;
import systems.porto.api.spi.OperationLog;
import systems.porto.api.schedule.QuartzJobScheduler;
import systems.porto.api.schedule.ScheduledJob;
import systems.porto.business.BusinessProcessor;
import systems.porto.context.Context;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PluginRuntime {
    private static final Logger logger = LoggerFactory.getLogger(PluginRuntime.class);

    private final PortoApiProperties properties;
    private final ApiContextFactory contextFactory;
    private final PluginRouteDispatcher routeDispatcher;
    private final PluginDynamicLoader dynamicLoader;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final QuartzJobScheduler quartzJobScheduler;
    private final AccessTokenService accessTokenService;
    private final SecretHasher secretHasher;

    @Getter
    private final ApiContext apiContext;
    private final ApiPluginClassLoader pluginClassLoader;

    public PluginRuntime(
        final PortoApiProperties properties,
        final ApiContextFactory contextFactory,
        final PluginRouteDispatcher routeDispatcher,
        final Validator validator,
        final ObjectMapper objectMapper,
        final QuartzJobScheduler quartzJobScheduler,
        final AccessTokenService accessTokenService,
        final SecretHasher secretHasher
    ) {
        this.properties = properties;
        this.contextFactory = contextFactory;
        this.routeDispatcher = routeDispatcher;
        this.dynamicLoader = new PluginDynamicLoader();
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.quartzJobScheduler = quartzJobScheduler;
        this.accessTokenService = accessTokenService;
        this.secretHasher = secretHasher;
        this.apiContext = contextFactory.create();
        this.apiContext.setRouteRegistrar(routeDispatcher);
        this.apiContext.setObjectMapper(objectMapper);
        this.pluginClassLoader = new ApiPluginClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public void initialize() {
        // porto-core Config types keep values in JVM-static maps (putIfAbsent). Clear them so
        // switching applications (or reloading plugins in tests) does not reuse another app's config.
        clearPortoCoreStaticConfigCaches();
        PluginRegistryFile registry = readRegistry();
        bindHostAuth();
        loadDtoJars(registry.getDtos());
        loadDatasources(registry.getDatasources());
        loadClientAdapters(registry.getClientAdapters());
        bindIdentityProvider();
        loadProcessors(registry.getProcessors());
        loadEntryAdapters(registry.getEntryAdapters());
        bindJobScheduler();
        loadScheduledJobs(registry.getScheduledJobs());
        provisionPersistedSchedules();
        logger.info("Plugin runtime initialized for application {}", properties.getApplication());
    }

    private void clearPortoCoreStaticConfigCaches() {
        clearStaticMap("systems.porto.adapter.config.Config", "configMap");
        clearStaticMap("systems.porto.business.config.Config", "propertyMap");
        clearStaticMap("systems.porto.business.config.Config", "connectorMap");
    }

    private void clearStaticMap(final String className, final String fieldName) {
        try {
            Class<?> type = Class.forName(className);
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                map.clear();
            }
        } catch (ReflectiveOperationException ex) {
            logger.debug("Could not clear {}.{}: {}", className, fieldName, ex.toString());
        }
    }

    public Object dispatch(
        final String method,
        final String path,
        final Map<String, String> queryParams,
        final Object requestBody
    ) {
        return dispatch(method, path, queryParams, requestBody, Map.of(), null);
    }

    public Object dispatch(
        final String method,
        final String path,
        final Map<String, String> queryParams,
        final Object requestBody,
        final Map<String, String> requestHeaders,
        final byte[] rawRequestBody
    ) {
        return dispatch(method, path, queryParams, requestBody, requestHeaders, rawRequestBody, null);
    }

    public Object dispatch(
        final String method,
        final String path,
        final Map<String, String> queryParams,
        final Object requestBody,
        final Map<String, String> requestHeaders,
        final byte[] rawRequestBody,
        final AuthenticatedPrincipal principal
    ) {
        PluginRoute route = routeDispatcher.match(method, path)
            .orElseThrow(() -> new PluginNotFoundException("No plugin route for " + method + " " + path));

        Optional<Integer> pathId = routeDispatcher.extractPathId(route.getPathPattern(), path);
        validateRequest(route.getOperation(), requestBody);

        BusinessProcessor<Context> processor = apiContext.getProcessor(route.getProcessorId());
        if (processor == null) {
            throw new PluginNotFoundException("Processor not found: " + route.getProcessorId());
        }

        RegistryInvocation invocation = new RegistryInvocation(
            apiContext,
            route.getOperation(),
            requestBody,
            queryParams,
            pathId,
            objectMapper,
            requestHeaders,
            rawRequestBody
        );
        invocation.setPrincipal(principal);
        if (principal != null) {
            OperationLog.bindUser(principal.username());
        }
        if (processor.getConfig() != null) {
            invocation.bindConnectorResolver(connectorId -> processor.getConfig().connectors().stream()
                .filter(connector -> connectorId.equals(connector.id()))
                .map(systems.porto.business.config.Connector::adapter)
                .filter(adapterId -> adapterId != null && !adapterId.isBlank())
                .findFirst()
                .orElse(null));
        }
        processor.process(invocation);
        return invocation.getResponse();
    }

    private void validateRequest(final String operation, final Object requestBody) {
        if (requestBody == null) {
            return;
        }
        String className = resolveValidationClassName(operation);
        if (className == null) {
            return;
        }
        try {
            Class<?> type = Class.forName(className, true, pluginClassLoader);
            Object target = objectMapper.convertValue(requestBody, type);
            Set<ConstraintViolation<Object>> violations = validator.validate(target);
            if (!violations.isEmpty()) {
                Map<String, String> errors = violations.stream()
                    .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a,
                        LinkedHashMap::new
                    ));
                throw new PluginValidationException(errors);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Validation type not found: " + className, e);
        }
    }

    /**
     * Maps {@code {kebab-entity}-create|update} to {@code Create|Update{Pascal}Request}
     * in the active application's DTO package.
     */
    private String resolveValidationClassName(final String operation) {
        if (operation == null || operation.isBlank()) {
            return null;
        }
        String suffix;
        String entityKebab;
        if (operation.endsWith("-create")) {
            suffix = "Create";
            entityKebab = operation.substring(0, operation.length() - "-create".length());
        } else if (operation.endsWith("-update")) {
            suffix = "Update";
            entityKebab = operation.substring(0, operation.length() - "-update".length());
        } else {
            return null;
        }
        if (entityKebab.isBlank()) {
            return null;
        }
        String dtoPackage = dtoPackageForApplication(properties.getApplication());
        return dtoPackage + "." + suffix + toPascalCase(entityKebab) + "Request";
    }

    private static String dtoPackageForApplication(final String application) {
        if ("shine-media".equals(application)) {
            return "systems.porto.shinemedia.dto";
        }
        if ("registry".equals(application)) {
            return "systems.porto.registry.dto";
        }
        String normalized = application == null ? "registry" : application.replace("-", "");
        return "systems.porto." + normalized + ".dto";
    }

    private static String toPascalCase(final String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (part.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private PluginRegistryFile readRegistry() {
        String env = properties.getEnvironment();
        Path appConfigDir = Path.of(properties.getApplicationPath());
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        PluginRegistryFile registry = new PluginRegistryFile();
        mergeYaml(yamlMapper, appConfigDir.resolve("dtos-" + env + ".yaml"), registry);
        mergeYaml(yamlMapper, appConfigDir.resolve("datasources-" + env + ".yaml"), registry);
        mergeYaml(yamlMapper, appConfigDir.resolve("client-adapters-" + env + ".yaml"), registry);
        mergeYaml(yamlMapper, appConfigDir.resolve("processors-" + env + ".yaml"), registry);
        mergeYaml(yamlMapper, appConfigDir.resolve("entry-adapters-" + env + ".yaml"), registry);
        mergeYaml(yamlMapper, appConfigDir.resolve("scheduled-jobs-" + env + ".yaml"), registry);
        return registry;
    }

    private void mergeYaml(final ObjectMapper yamlMapper, final Path path, final PluginRegistryFile registry) {
        if (!path.toFile().exists()) {
            return;
        }
        try {
            PluginRegistryFile fragment = yamlMapper.readValue(path.toFile(), PluginRegistryFile.class);
            if (fragment.getDtos() != null) {
                registry.getDtos().addAll(fragment.getDtos());
            }
            if (fragment.getDatasources() != null) {
                registry.getDatasources().addAll(fragment.getDatasources());
            }
            if (fragment.getClientAdapters() != null) {
                registry.getClientAdapters().addAll(fragment.getClientAdapters());
            }
            if (fragment.getProcessors() != null) {
                registry.getProcessors().addAll(fragment.getProcessors());
            }
            if (fragment.getEntryAdapters() != null) {
                registry.getEntryAdapters().addAll(fragment.getEntryAdapters());
            }
            if (fragment.getScheduledJobs() != null) {
                registry.getScheduledJobs().addAll(fragment.getScheduledJobs());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read plugin registry: " + path, e);
        }
    }

    private void loadDtoJars(final List<PluginDescriptor> descriptors) {
        for (PluginDescriptor descriptor : descriptors) {
            String pluginPath = resolveContractPluginPath(descriptor);
            dynamicLoader.addJarDependencies(pluginPath, descriptor, pluginClassLoader);
            logger.info("Loaded contract jar: {}", descriptor.getJarFile());
        }
    }

    private String resolveContractPluginPath(final PluginDescriptor descriptor) {
        String id = descriptor.getId() != null ? descriptor.getId() : "";
        if (id.contains("common") || "porto-api-common".equals(id)) {
            return pluginsPath("common");
        }
        return pluginsPath("dtos");
    }

    private void loadDatasources(final List<PluginDescriptor> descriptors) {
        String pluginPath = pluginsPath("datasources");
        for (PluginDescriptor descriptor : descriptors) {
            Adapter<Context> adapter = dynamicLoader.loadComponent(pluginPath, descriptor, pluginClassLoader);
            adapter.init(apiContext);
            apiContext.addAdapter(descriptor.getId(), adapter);
            logger.info("Loaded datasource plugin: {}", descriptor.getId());
        }
    }

    private void loadClientAdapters(final List<PluginDescriptor> descriptors) {
        String pluginPath = pluginsPath("client-adapters");
        for (PluginDescriptor descriptor : descriptors) {
            ClientAdapter<Context> adapter = dynamicLoader.loadComponent(pluginPath, descriptor, pluginClassLoader);
            adapter.init(apiContext);
            apiContext.addClientAdapter(descriptor.getId(), adapter);
            logger.info("Loaded client adapter: {}", descriptor.getId());
        }
    }

    private void loadProcessors(final List<PluginDescriptor> descriptors) {
        String pluginPath = pluginsPath("processors");
        for (PluginDescriptor descriptor : descriptors) {
            BusinessProcessor<Context> processor = dynamicLoader.loadComponent(pluginPath, descriptor, pluginClassLoader);
            processor.init(apiContext);
            // porto-core reads plugins/processors/{id}-{env}.yaml; prefer app-scoped file when present.
            reloadBusinessProcessorConfig(processor, descriptor.getId());
            apiContext.addProcessor(descriptor.getId(), processor);
            logger.info("Loaded processor: {}", descriptor.getId());
        }
    }

    private void loadEntryAdapters(final List<PluginDescriptor> descriptors) {
        String pluginPath = pluginsPath("entry-adapters");
        for (PluginDescriptor descriptor : descriptors) {
            EntryAdapter<Context> entryAdapter = dynamicLoader.loadComponent(pluginPath, descriptor, pluginClassLoader);
            entryAdapter.init(apiContext);
            reloadEntryAdapterConfig(entryAdapter, descriptor.getId());
            apiContext.addEntryAdapter(descriptor.getId(), entryAdapter);
            entryAdapter.start();
            logger.info("Started entry adapter: {}", descriptor.getId());
        }
    }

    private void bindHostAuth() {
        apiContext.setVariable(ApiContextConstants.CONTEXT_ACCESS_TOKEN_SERVICE, accessTokenService);
        apiContext.setVariable(ApiContextConstants.CONTEXT_SECRET_HASHER, secretHasher);
    }

    private void bindIdentityProvider() {
        String providerId = properties.getAuth().getIdentityProvider();
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        Object adapter = apiContext.findClientAdapter(providerId);
        if (adapter instanceof IdentityProvider identityProvider) {
            apiContext.setVariable(ApiContextConstants.CONTEXT_IDENTITY_PROVIDER, identityProvider);
            logger.info("Bound identity provider plugin {}", providerId);
            return;
        }
        if (adapter != null) {
            throw new IllegalStateException(
                "Identity provider plugin '" + providerId + "' does not implement "
                    + IdentityProvider.class.getName()
            );
        }
        if (properties.getAuth().isRequired()) {
            throw new IllegalStateException(
                "Auth is required but identity provider plugin '" + providerId + "' is not loaded"
            );
        }
        logger.info("Identity provider plugin {} is not loaded", providerId);
    }

    private void bindJobScheduler() {
        quartzJobScheduler.clear();
        quartzJobScheduler.bindHost(apiContext);
        apiContext.setVariable(ApiContextConstants.CONTEXT_JOB_SCHEDULER, quartzJobScheduler);
    }

    private void loadScheduledJobs(final List<PluginDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }
        String pluginPath = pluginsPath("scheduled-jobs");
        for (PluginDescriptor descriptor : descriptors) {
            Object component = dynamicLoader.loadComponent(pluginPath, descriptor, pluginClassLoader);
            if (!(component instanceof ScheduledJob scheduledJob)) {
                throw new IllegalStateException(
                    "scheduled-jobs plugin '" + descriptor.getId() + "' must implement "
                        + ScheduledJob.class.getName()
                );
            }
            if (!descriptor.getId().equals(scheduledJob.id())) {
                throw new IllegalStateException(
                    "scheduled-jobs plugin id '" + descriptor.getId()
                        + "' does not match ScheduledJob.id() '" + scheduledJob.id() + "'"
                );
            }
            scheduledJob.bindConnectors(scheduledJobConnectors(descriptor.getId()));
            scheduledJob.bindProperties(scheduledJobProperties(descriptor.getId()));
            quartzJobScheduler.registerJob(scheduledJob);
        }
    }

    private Map<String, String> scheduledJobConnectors(final String pluginId) {
        ProcessorPluginYaml yaml = readScheduledJobYaml(pluginId);
        if (yaml == null || yaml.connectors() == null || yaml.connectors().isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (systems.porto.business.config.Connector connector : yaml.connectors()) {
            if (connector != null && connector.id() != null && connector.adapter() != null) {
                values.put(connector.id(), connector.adapter());
            }
        }
        return values;
    }

    private Map<String, String> scheduledJobProperties(final String pluginId) {
        ProcessorPluginYaml yaml = readScheduledJobYaml(pluginId);
        if (yaml == null || yaml.properties() == null || yaml.properties().isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (systems.porto.business.config.Property property : yaml.properties()) {
            if (property != null && property.key() != null) {
                values.put(property.key(), property.value());
            }
        }
        return values;
    }

    private ProcessorPluginYaml readScheduledJobYaml(final String pluginId) {
        Path path = resolvePluginYaml("scheduled-jobs", pluginId);
        if (path == null) {
            return null;
        }
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            return yamlMapper.readValue(path.toFile(), ProcessorPluginYaml.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read scheduled-job config: " + path, ex);
        }
    }

    private void provisionPersistedSchedules() {
        BusinessProcessor<Context> processor = apiContext.getProcessor("scheduled-job-crud");
        if (processor == null) {
            return;
        }
        RegistryInvocation invocation = new RegistryInvocation(
            apiContext,
            "scheduled-job-provision-all",
            null,
            Map.of(),
            Optional.empty(),
            objectMapper
        );
        if (processor.getConfig() != null) {
            invocation.bindConnectorResolver(connectorId -> processor.getConfig().connectors().stream()
                .filter(connector -> connectorId.equals(connector.id()))
                .map(systems.porto.business.config.Connector::adapter)
                .filter(adapterId -> adapterId != null && !adapterId.isBlank())
                .findFirst()
                .orElse(null));
        }
        processor.process(invocation);
        logger.info("Provisioned persisted scheduled jobs into the host scheduler");
    }

    /**
     * Plugin JARs and YAML live under {@code plugins/{type}/{application}/}, with
     * infrastructural adapters in {@code plugins/{type}/shared/}, shared contracts in
     * {@code plugins/common/}, and a flat {@code plugins/{type}/} fallback.
     */
    private String pluginsPath(final String type) {
        String application = properties.getApplication();
        Path appScoped = Path.of(properties.getHomeDirectory(), "plugins", type, application);
        if (Files.isDirectory(appScoped)) {
            return appScoped.toString();
        }
        return Path.of(properties.getHomeDirectory(), "plugins", type).toString();
    }

    private void reloadBusinessProcessorConfig(final BusinessProcessor<Context> processor, final String pluginId) {
        Path path = resolvePluginYaml("processors", pluginId);
        if (path == null) {
            return;
        }
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            ProcessorPluginYaml yaml = yamlMapper.readValue(path.toFile(), ProcessorPluginYaml.class);
            systems.porto.business.config.Config config = systems.porto.business.config.Config.builder()
                .connectors(yaml.connectors() != null ? yaml.connectors() : List.of())
                .properties(yaml.properties() != null ? yaml.properties() : List.of())
                .build();
            processor.setConfig(config);
            logger.debug("Loaded app-scoped processor config from {}", path);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read processor config: " + path, ex);
        }
    }

    private void reloadEntryAdapterConfig(final EntryAdapter<Context> entryAdapter, final String pluginId) {
        Path path = resolvePluginYaml("entry-adapters", pluginId);
        if (path == null) {
            return;
        }
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            systems.porto.adapter.config.Config config =
                yamlMapper.readValue(path.toFile(), systems.porto.adapter.config.Config.class);
            if (!(entryAdapter instanceof AbstractEntryAdapter<?> abstractEntryAdapter)) {
                throw new IllegalStateException(
                    "Entry adapter '" + pluginId + "' does not support config reload"
                );
            }
            abstractEntryAdapter.setConfig(config);
            logger.debug("Loaded app-scoped entry-adapter config from {}", path);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read entry-adapter config: " + path, ex);
        }
    }

    private Path resolvePluginYaml(final String type, final String pluginId) {
        String fileName = pluginId + "-" + properties.getEnvironment() + ".yaml";
        Path appScoped = Path.of(
            properties.getHomeDirectory(), "plugins", type, properties.getApplication(), fileName
        );
        if (Files.isRegularFile(appScoped)) {
            return appScoped;
        }
        Path shared = Path.of(properties.getHomeDirectory(), "plugins", type, "shared", fileName);
        if (Files.isRegularFile(shared)) {
            return shared;
        }
        Path flat = Path.of(properties.getHomeDirectory(), "plugins", type, fileName);
        return Files.isRegularFile(flat) ? flat : null;
    }

    public static class PluginNotFoundException extends RuntimeException {
        public PluginNotFoundException(final String message) {
            super(message);
        }
    }

    public static class PluginValidationException extends RuntimeException {
        @Getter
        private final Map<String, String> errors;

        public PluginValidationException(final Map<String, String> errors) {
            super("Validation failed");
            this.errors = errors;
        }
    }
}
