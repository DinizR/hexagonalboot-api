package systems.porto.api.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import systems.porto.api.auth.SecretHasher;

@Component
public class BcryptSecretHasher implements SecretHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(final String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("Secret is required");
        }
        return encoder.encode(rawSecret);
    }

    @Override
    public boolean matches(final String rawSecret, final String storedHash) {
        if (rawSecret == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return encoder.matches(rawSecret, storedHash);
    }
}
