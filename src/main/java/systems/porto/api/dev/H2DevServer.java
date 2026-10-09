package systems.porto.api.dev;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only embedded H2 TCP server so multiple clients (porto-api, IntelliJ, other apps)
 * can share the same database without file-lock conflicts.
 *
 * <p>Each application uses {@code data/{databaseName}/} as the H2 base directory and
 * JDBC URL {@code jdbc:h2:tcp://127.0.0.1:9092/{databaseName}} with matching credentials.
 */
public final class H2DevServer {
    private static final Logger logger = LoggerFactory.getLogger(H2DevServer.class);
    private static final int TCP_PORT = 9092;
    private static final Pattern APPLICATION_PATH_PATTERN =
        Pattern.compile("^\\s*application-path:\\s*(.+)$");

    private static Server tcpServer;
    private static boolean shutdownHookRegistered;
    private static String activeDatabaseName;

    private H2DevServer() {
    }

    public static void ensureRunning(final String homeDirectory) throws SQLException, IOException {
        ensureRunning(homeDirectory, resolveDatabaseName(homeDirectory));
    }

    public static void ensureRunning(final String homeDirectory, final String databaseName)
        throws SQLException, IOException {
        String name = databaseName == null || databaseName.isBlank() ? "registry" : databaseName.trim();
        activeDatabaseName = name;

        if (isReachable(name)) {
            logger.info("H2 dev TCP server already available on port {} (db={})", TCP_PORT, name);
            return;
        }

        if (tcpServer != null && tcpServer.isRunning(false)) {
            return;
        }

        Path baseDir = Path.of(homeDirectory, "data", name);
        Files.createDirectories(baseDir);

        try {
            tcpServer = Server.createTcpServer(
                "-tcpPort", String.valueOf(TCP_PORT),
                "-tcpAllowOthers",
                "-ifNotExists",
                "-baseDir", baseDir.toAbsolutePath().toString()
            );
            tcpServer.start();
            registerShutdownHook();
            logger.info("H2 dev TCP server started on port {} (baseDir={})", TCP_PORT, baseDir.toAbsolutePath());
            logger.info("Clients connect with: {}", jdbcUrl(name));
        } catch (SQLException ex) {
            if (isPortInUse(ex) && isReachable(name)) {
                logger.info("Reusing existing H2 dev TCP server on port {} (db={})", TCP_PORT, name);
                return;
            }
            throw ex;
        }
    }

    /**
     * Resolves the H2 database name for the active application.
     * Order: {@code API_APPLICATION} env, {@code application-path} in {@code config/api-{env}.yml},
     * then {@code registry}.
     */
    public static String resolveDatabaseName(final String homeDirectory) {
        String fromEnv = System.getenv("API_APPLICATION");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String environment = System.getenv().getOrDefault("API_ENV", "dev");
        Path apiConfig = Path.of(homeDirectory, "config", "api-" + environment + ".yml");
        if (Files.isRegularFile(apiConfig)) {
            try {
                for (String line : Files.readAllLines(apiConfig)) {
                    Matcher matcher = APPLICATION_PATH_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String value = matcher.group(1).trim();
                        int comment = value.indexOf('#');
                        if (comment >= 0) {
                            value = value.substring(0, comment).trim();
                        }
                        value = value.replace("${porto.api.home-directory}", homeDirectory);
                        Path path = Path.of(value);
                        Path fileName = path.getFileName();
                        if (fileName != null && !fileName.toString().isBlank()) {
                            return fileName.toString();
                        }
                    }
                }
            } catch (IOException ex) {
                logger.warn("Could not read {} to resolve H2 database name: {}", apiConfig, ex.toString());
            }
        }
        return "registry";
    }

    public static void stop() {
        if (tcpServer != null) {
            tcpServer.stop();
            tcpServer = null;
            logger.info("H2 dev TCP server stopped");
        }
    }

    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(H2DevServer::stop, "h2-dev-server-shutdown"));
            shutdownHookRegistered = true;
        }
    }

    private static String jdbcUrl(final String databaseName) {
        return "jdbc:h2:tcp://127.0.0.1:" + TCP_PORT + "/" + databaseName;
    }

    private static boolean isReachable(final String databaseName) {
        try (Connection connection = DriverManager.getConnection(
            jdbcUrl(databaseName), databaseName, databaseName)) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean isPortInUse(final SQLException ex) {
        if (ex.getErrorCode() == 90061) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && (message.contains("Address already in use") || message.contains("port may be in use"));
    }
}
