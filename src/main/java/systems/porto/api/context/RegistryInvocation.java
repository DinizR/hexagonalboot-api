package systems.porto.api.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import systems.porto.adapter.Adapter;
import systems.porto.adapter.client.ClientAdapter;
import systems.porto.api.auth.AuthenticatedPrincipal;
import systems.porto.api.route.RouteRegistrar;
import systems.porto.api.spi.RequestContext;
import systems.porto.context.Context;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Per-call request envelope passed to business processors.
 * Delegates host/app services to {@link ApiContext}; does not store request state on the app context.
 */
public final class RegistryInvocation implements RequestContext {
    private final ApiContext apiContext;
    private final String operation;
    private final Object requestBody;
    private final Map<String, String> queryParams;
    private final Optional<Integer> pathId;
    private final ObjectMapper objectMapper;
    private final Map<String, String> requestHeaders;
    private final byte[] rawRequestBody;
    private Object response;
    private Function<String, String> connectorResolver;
    private AuthenticatedPrincipal principal;

    public RegistryInvocation(
        final ApiContext apiContext,
        final String operation,
        final Object requestBody,
        final Map<String, String> queryParams,
        final Optional<Integer> pathId,
        final ObjectMapper objectMapper
    ) {
        this(apiContext, operation, requestBody, queryParams, pathId, objectMapper, Map.of(), null);
    }

    public RegistryInvocation(
        final ApiContext apiContext,
        final String operation,
        final Object requestBody,
        final Map<String, String> queryParams,
        final Optional<Integer> pathId,
        final ObjectMapper objectMapper,
        final Map<String, String> requestHeaders,
        final byte[] rawRequestBody
    ) {
        this.apiContext = apiContext;
        this.operation = operation;
        this.requestBody = requestBody;
        this.queryParams = queryParams != null ? queryParams : Map.of();
        this.pathId = pathId;
        this.objectMapper = objectMapper;
        this.requestHeaders = copyHeaders(requestHeaders);
        this.rawRequestBody = rawRequestBody;
    }

    private static Map<String, String> copyHeaders(final Map<String, String> requestHeaders) {
        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        requestHeaders.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    @Override
    public String getOperation() {
        return operation;
    }

    @Override
    public String getHomeDirectory() {
        return apiContext.getHomeDirectory();
    }

    @Override
    public String getEnvironment() {
        return apiContext.getEnvironment();
    }

    @Override
    public String getApplication() {
        return apiContext.getApplication();
    }

    @Override
    public Optional<Object> getVariable(final String key) {
        return apiContext.getVariable(key);
    }

    @Override
    public void setVariable(final String key, final Object value) {
        apiContext.setVariable(key, value);
    }

    @Override
    public DataSource getDataSource(final String id) {
        return apiContext.getDataSource(id);
    }

    @Override
    public RouteRegistrar getRouteRegistrar() {
        return apiContext.getRouteRegistrar();
    }

    @Override
    public void setResponse(final Object response) {
        this.response = response;
    }

    @Override
    public Object getResponse() {
        return response;
    }

    @Override
    public Optional<Integer> getPathId() {
        return pathId;
    }

    @Override
    public Optional<String> getQueryParam(final String name) {
        return Optional.ofNullable(queryParams.get(name));
    }

    @Override
    public int getIntQueryParam(final String name, final int defaultValue) {
        return getQueryParam(name)
            .map(Integer::parseInt)
            .orElse(defaultValue);
    }

    @Override
    public <T> T getRequestBody(final Class<T> type) {
        if (requestBody == null) {
            return null;
        }
        if (objectMapper == null) {
            throw new IllegalStateException("ObjectMapper is required for request body conversion");
        }
        return objectMapper.convertValue(requestBody, type);
    }

    @Override
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Optional<byte[]> getRawRequestBody() {
        return Optional.ofNullable(rawRequestBody);
    }

    @Override
    public ClientAdapter<Context> getClientAdapter(final String id) {
        return apiContext.getClientAdapter(id);
    }

    @Override
    public void bindConnectorResolver(final Function<String, String> connectorIdToAdapterId) {
        this.connectorResolver = connectorIdToAdapterId;
    }

    @Override
    public <T> T requireClientAdapter(final String connectorId, final Class<T> type) {
        String adapterId = resolveAdapterId(connectorId);
        ClientAdapter<Context> adapter = apiContext.getClientAdapter(adapterId);
        if (adapter == null) {
            throw new IllegalStateException(
                "Client adapter '" + adapterId + "' not found for connector '" + connectorId + "'"
            );
        }
        return castCapability(adapter, adapterId, connectorId, type);
    }

    @Override
    public Optional<AuthenticatedPrincipal> principal() {
        return Optional.ofNullable(principal);
    }

    @Override
    public void setPrincipal(final AuthenticatedPrincipal principal) {
        this.principal = principal;
    }

    @Override
    public <T> T requireAdapter(final String connectorId, final Class<T> type) {
        String adapterId = resolveAdapterId(connectorId);
        Adapter<Context> adapter = apiContext.getAdapter(adapterId);
        if (adapter == null) {
            throw new IllegalStateException(
                "Adapter '" + adapterId + "' not found for connector '" + connectorId + "'"
            );
        }
        return castCapability(adapter, adapterId, connectorId, type);
    }

    private String resolveAdapterId(final String connectorId) {
        if (connectorResolver == null) {
            throw new IllegalStateException(
                "Connector resolver is not bound; cannot resolve connector '" + connectorId + "'"
            );
        }
        String adapterId = connectorResolver.apply(connectorId);
        if (adapterId == null || adapterId.isBlank()) {
            throw new IllegalStateException(
                "No adapter mapped for connector '" + connectorId + "' in processor config"
            );
        }
        return adapterId;
    }

    private static <T> T castCapability(
        final Object adapter,
        final String adapterId,
        final String connectorId,
        final Class<T> type
    ) {
        if (!type.isInstance(adapter)) {
            throw new IllegalStateException(
                "Adapter '" + adapterId + "' for connector '" + connectorId
                    + "' does not implement " + type.getName()
                    + " (actual: " + adapter.getClass().getName() + ")"
            );
        }
        return type.cast(adapter);
    }
}
