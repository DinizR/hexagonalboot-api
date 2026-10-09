package systems.porto.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@ConfigurationProperties(prefix = "porto.api")
public class PortoApiProperties {
    private String application;
    private String environment = "dev";
    private String homeDirectory = ".";
    private String configPath;
    private String applicationPath;
    private String pluginsPath;
    private String apiBasePath = "/api/v1.0.0";
    private Auth auth = new Auth();

    @Data
    public static class Auth {
        /**
         * When true, every application HTTP route requires a valid bearer token
         * except {@code /auth/token} and host docs/health. Expired local passwords
         * may still obtain a token, then must {@code POST /auth/password} before
         * other API calls. Shine Media turns this on;
         * Registry stays open until it loads an IdP.
         */
        private boolean required = false;
        private String identityProvider = "local-idp";
        private Jwt jwt = new Jwt();
    }

    @Data
    public static class Jwt {
        private String issuer = "hexagonalboot";
        /**
         * HMAC-SHA256 secret, at least 32 characters. Override in production
         * with {@code PORTO_JWT_SECRET}.
         */
        private String secret = "hexagonalboot-local-jwt-secret-32b";
        private String timeToLive = "PT8H";
    }

    /**
     * Application id (e.g. {@code registry}) — derived from the last segment of
     * {@link #applicationPath} when not set explicitly, same as porto-mpos derives
     * the app properties filename from {@code terminal.application.path}.
     */
    public String getApplication() {
        if (application != null && !application.isBlank()) {
            return application;
        }
        if (applicationPath != null && !applicationPath.isBlank()) {
            Path path = Paths.get(applicationPath);
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }
        return "registry";
    }
}
