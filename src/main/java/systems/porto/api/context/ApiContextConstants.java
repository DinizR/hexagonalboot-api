package systems.porto.api.context;

/**
 * Keys and prefixes for {@link ApiContext} variables shared between the host and plugins.
 */
public final class ApiContextConstants {
    public static final String CONTEXT_HOME_DIRECTORY = "homeDirectory";
    public static final String CONTEXT_ENVIRONMENT = "environment";
    public static final String CONTEXT_APPLICATION = "application";

    public static final String CONTEXT_PROPERTIES = "properties";
    public static final String CONTEXT_APPLICATION_CONTEXT = "applicationContext";
    public static final String CONTEXT_ROUTE_REGISTRAR = "routeRegistrar";
    public static final String CONTEXT_OBJECT_MAPPER = "objectMapper";

    /** Prefix for datasource variables: {@code datasources.{id}}. */
    public static final String CONTEXT_DATASOURCES = "datasources";

    public static final String CONTEXT_PROCESSORS = "processors";
    public static final String CONTEXT_ENTRY_ADAPTERS = "entryAdapters";
    public static final String CONTEXT_CLIENT_ADAPTERS = "clientAdapters";
    public static final String CONTEXT_ADAPTERS = "adapters";
    public static final String CONTEXT_JOB_SCHEDULER = "jobScheduler";
    public static final String CONTEXT_ACCESS_TOKEN_SERVICE = "accessTokenService";
    public static final String CONTEXT_IDENTITY_PROVIDER = "identityProvider";
    public static final String CONTEXT_SECRET_HASHER = "secretHasher";

    private ApiContextConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String datasourceKey(final String id) {
        return CONTEXT_DATASOURCES + "." + id;
    }
}
