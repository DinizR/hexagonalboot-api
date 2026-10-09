package systems.porto.api;

/**
 * Resolves the runtime home (config, plugins, H2 {@code data/}, DocumentStorage).
 * {@code PORTO_API_HOME} is the canonical name (like {@code MPOS_HOME}); {@code API_HOME}
 * remains an alias. The directory is a product deploy (not this git working tree).
 */
public final class PortoApiHome {

    public static final String ENV_PORTO_API_HOME = "PORTO_API_HOME";
    public static final String ENV_API_HOME = "API_HOME";
    static final String MISSING_HOME_MESSAGE =
        "Set PORTO_API_HOME to a deploy directory (for example ~/projects/porto-workspace/shine-media-api-deploy). "
            + "The porto-api working tree is not a runtime home.";

    private PortoApiHome() {
    }

    public static String resolve() {
        String raw = firstNonBlank(
            System.getenv(ENV_PORTO_API_HOME),
            System.getProperty(ENV_PORTO_API_HOME),
            System.getenv(ENV_API_HOME),
            System.getProperty(ENV_API_HOME)
        );
        if (raw == null) {
            throw new IllegalStateException(MISSING_HOME_MESSAGE);
        }
        return expandUserHome(raw);
    }

    /** Publish the resolved path so Spring and Logback see the same home. */
    public static void applyToSystemProperties() {
        String home = resolve();
        System.setProperty(ENV_PORTO_API_HOME, home);
        System.setProperty(ENV_API_HOME, home);
    }

    private static String firstNonBlank(final String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static String expandUserHome(final String path) {
        if (path.startsWith("~/") || path.equals("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
