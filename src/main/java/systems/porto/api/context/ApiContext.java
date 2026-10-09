package systems.porto.api.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;
import systems.porto.adapter.Adapter;
import systems.porto.adapter.client.ClientAdapter;
import systems.porto.adapter.entry.EntryAdapter;
import systems.porto.api.plugin.PluginRouteDispatcher;
import systems.porto.business.BusinessProcessor;
import systems.porto.context.Context;
import systems.porto.api.route.RouteRegistrar;
import systems.porto.api.spi.HostContext;

import javax.sql.DataSource;
import java.util.*;

/**
 * Application context for porto-api: shared variables, plugin handlers, and host services.
 * Analogous to POS context — app-level only; per-request data lives in {@link RegistryInvocation}.
 */
public class ApiContext implements Context, HostContext {
    private static final String PROCESSOR_VARIABLE_PREFIX = ApiContextConstants.CONTEXT_PROCESSORS + ".";
    private static final String ENTRY_ADAPTER_VARIABLE_PREFIX = ApiContextConstants.CONTEXT_ENTRY_ADAPTERS + ".";
    private static final String CLIENT_ADAPTER_VARIABLE_PREFIX = ApiContextConstants.CONTEXT_CLIENT_ADAPTERS + ".";
    private static final String ADAPTER_VARIABLE_PREFIX = ApiContextConstants.CONTEXT_ADAPTERS + ".";

    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, EntryAdapter<Context>> entryAdapters = new HashMap<>();
    private final Map<String, BusinessProcessor<Context>> processors = new HashMap<>();
    private final Map<String, ClientAdapter<Context>> clientAdapters = new HashMap<>();
    private final Map<String, Adapter<Context>> adapters = new HashMap<>();

    public ApiContext(final String homeDirectory, final String environment, final String application) {
        setHomeDirectory(homeDirectory);
        setEnvironment(environment);
        setApplication(application);
        Properties properties = new Properties();
        properties.putAll(System.getProperties());
        setVariable(ApiContextConstants.CONTEXT_PROPERTIES, properties);
    }

    @Override
    public String getHomeDirectory() {
        return getStringVariable(ApiContextConstants.CONTEXT_HOME_DIRECTORY);
    }

    public void setHomeDirectory(final String homeDirectory) {
        setVariable(ApiContextConstants.CONTEXT_HOME_DIRECTORY, homeDirectory);
    }

    @Override
    public String getEnvironment() {
        return getStringVariable(ApiContextConstants.CONTEXT_ENVIRONMENT);
    }

    public void setEnvironment(final String environment) {
        setVariable(ApiContextConstants.CONTEXT_ENVIRONMENT, environment);
    }

    @Override
    public String getApplication() {
        return getStringVariable(ApiContextConstants.CONTEXT_APPLICATION);
    }

    public void setApplication(final String application) {
        setVariable(ApiContextConstants.CONTEXT_APPLICATION, application);
    }

    @Override
    public Optional<Object> getVariable(final String variable) {
        return Optional.ofNullable(variables.get(variable));
    }

    @Override
    public void setVariable(final String variable, final Object value) {
        if (value == null) {
            variables.remove(variable);
        } else {
            variables.put(variable, value);
        }
    }

    public List<String> getAllVariables() {
        return variables.keySet().stream().toList();
    }

    public Properties getProperties() {
        return getVariable(ApiContextConstants.CONTEXT_PROPERTIES)
            .filter(Properties.class::isInstance)
            .map(Properties.class::cast)
            .orElseGet(Properties::new);
    }

    public void setProperties(final Properties properties) {
        setVariable(ApiContextConstants.CONTEXT_PROPERTIES, properties != null ? properties : new Properties());
    }

    public Optional<String> getProperty(final String key) {
        return Optional.ofNullable(getProperties().getProperty(key));
    }

    public String getProperty(final String key, final String defaultValue) {
        return getProperties().getProperty(key, defaultValue);
    }

    public Optional<String> getEnvironmentVariable(final String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    public String getEnvironmentVariable(final String name, final String defaultValue) {
        return getEnvironmentVariable(name).orElse(defaultValue);
    }

    public ApplicationContext getApplicationContext() {
        return getVariable(ApiContextConstants.CONTEXT_APPLICATION_CONTEXT)
            .filter(ApplicationContext.class::isInstance)
            .map(ApplicationContext.class::cast)
            .orElse(null);
    }

    public void setApplicationContext(final ApplicationContext applicationContext) {
        setVariable(ApiContextConstants.CONTEXT_APPLICATION_CONTEXT, applicationContext);
    }

    @Override
    public RouteRegistrar getRouteRegistrar() {
        return getVariable(ApiContextConstants.CONTEXT_ROUTE_REGISTRAR)
            .filter(RouteRegistrar.class::isInstance)
            .map(RouteRegistrar.class::cast)
            .orElse(null);
    }

    public void setRouteRegistrar(final PluginRouteDispatcher routeRegistrar) {
        setVariable(ApiContextConstants.CONTEXT_ROUTE_REGISTRAR, routeRegistrar);
    }

    public ObjectMapper getObjectMapper() {
        return getVariable(ApiContextConstants.CONTEXT_OBJECT_MAPPER)
            .filter(ObjectMapper.class::isInstance)
            .map(ObjectMapper.class::cast)
            .orElse(null);
    }

    public void setObjectMapper(final ObjectMapper objectMapper) {
        setVariable(ApiContextConstants.CONTEXT_OBJECT_MAPPER, objectMapper);
    }

    public void addDataSource(final String id, final DataSource dataSource) {
        setVariable(ApiContextConstants.datasourceKey(id), dataSource);
    }

    @Override
    public DataSource getDataSource(final String id) {
        return getVariable(ApiContextConstants.datasourceKey(id))
            .filter(DataSource.class::isInstance)
            .map(DataSource.class::cast)
            .orElse(null);
    }

    public void addProcessor(final String id, final BusinessProcessor<Context> processor) {
        processors.put(id, processor);
    }

    public void addProcessor(final String id, final Object descriptor, final BusinessProcessor<Context> processor) {
        setVariable(PROCESSOR_VARIABLE_PREFIX + id, descriptor);
        addProcessor(id, processor);
    }

    public BusinessProcessor<Context> getProcessor(final String id) {
        return processors.get(id);
    }

    public Set<String> getProcessorIds() {
        return Set.copyOf(processors.keySet());
    }

    public void addEntryAdapter(final String id, final EntryAdapter<Context> entryAdapter) {
        entryAdapters.put(id, entryAdapter);
    }

    public void addEntryAdapter(final String id, final Object descriptor, final EntryAdapter<Context> entryAdapter) {
        setVariable(ENTRY_ADAPTER_VARIABLE_PREFIX + id, descriptor);
        addEntryAdapter(id, entryAdapter);
    }

    public EntryAdapter<Context> getEntryAdapter(final String id) {
        return entryAdapters.get(id);
    }

    public Set<String> getEntryAdapterIds() {
        return Set.copyOf(entryAdapters.keySet());
    }

    public void addClientAdapter(final String id, final ClientAdapter<Context> clientAdapter) {
        clientAdapters.put(id, clientAdapter);
    }

    public void addClientAdapter(final String id, final Object descriptor, final ClientAdapter<Context> clientAdapter) {
        setVariable(CLIENT_ADAPTER_VARIABLE_PREFIX + id, descriptor);
        addClientAdapter(id, clientAdapter);
    }

    public ClientAdapter<Context> getClientAdapter(final String id) {
        return clientAdapters.get(id);
    }

    @Override
    public Object findClientAdapter(final String id) {
        return getClientAdapter(id);
    }

    public Set<String> getClientAdapterIds() {
        return Set.copyOf(clientAdapters.keySet());
    }

    public void addAdapter(final String id, final Adapter<Context> adapter) {
        adapters.put(id, adapter);
    }

    public void addAdapter(final String id, final Object descriptor, final Adapter<Context> adapter) {
        setVariable(ADAPTER_VARIABLE_PREFIX + id, descriptor);
        addAdapter(id, adapter);
    }

    public Adapter<Context> getAdapter(final String id) {
        return adapters.get(id);
    }

    public Set<String> getAdapterIds() {
        return Set.copyOf(adapters.keySet());
    }

    private String getStringVariable(final String key) {
        Object value = variables.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }
}
