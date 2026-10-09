package systems.porto.api.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.stereotype.Component;
import systems.porto.api.config.PortoApiProperties;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host JWT issuer/validator (HMAC-SHA256). Plugins never import Nimbus.
 */
@Component
public class NimbusAccessTokenService implements AccessTokenService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Set<String> IGNORED_IDP_ROLES = Set.of(
        "offline_access", "uma_authorization", "default-roles-shine-media", "default-roles-master"
    );

    private final PortoApiProperties properties;
    private final ConcurrentHashMap<String, JWKSource<SecurityContext>> jwkSources = new ConcurrentHashMap<>();

    public NimbusAccessTokenService(final PortoApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public AccessToken issue(final Identity identity, final TokenIssueOptions options) {
        if (identity == null || identity.subject() == null || identity.subject().isBlank()) {
            throw new IllegalArgumentException("Identity subject is required to issue a token");
        }
        Instant issuedAt = Instant.now();
        Duration ttl = resolveTtl(options);
        Instant expiresAt = issuedAt.plus(ttl);
        String issuer = issuer();
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(identity.subject())
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("username", identity.username())
                .claim("email", identity.email())
                .claim("profiles", List.copyOf(identity.profiles()));
            if (options != null && options.audience() != null && !options.audience().isBlank()) {
                claims.audience(options.audience());
            }
            if (options != null && options.extraClaims() != null) {
                options.extraClaims().forEach(claims::claim);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secretBytes()));
            return AccessToken.bearer(jwt.serialize(), issuedAt, expiresAt, issuer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign access token", ex);
        }
    }

    @Override
    public TokenValidationResult validate(final String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenValidationResult.invalid("missing_token");
        }
        try {
            SignedJWT jwt = SignedJWT.parse(rawToken);
            if (!jwt.verify(new MACVerifier(secretBytes()))) {
                return TokenValidationResult.invalid("invalid_signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                return TokenValidationResult.invalid("expired");
            }
            String expectedIssuer = issuer();
            if (expectedIssuer != null && !expectedIssuer.equals(claims.getIssuer())) {
                return TokenValidationResult.invalid("invalid_issuer");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return TokenValidationResult.invalid("missing_subject");
            }
            Identity identity = new Identity(
                subject,
                stringClaim(claims, "username"),
                stringClaim(claims, "email"),
                stringSetClaim(claims, "profiles"),
                stringSetClaim(claims, "permissions"),
                java.util.Map.of()
            );
            return TokenValidationResult.valid(identity);
        } catch (ParseException | JOSEException ex) {
            return TokenValidationResult.invalid("malformed_token");
        }
    }

    @Override
    public TokenValidationResult validateExternal(final String rawToken, final ExternalTokenConstraints constraints) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenValidationResult.invalid("missing_token");
        }
        if (constraints == null || constraints.issuer() == null || constraints.issuer().isBlank()
            || constraints.jwksUri() == null || constraints.jwksUri().isBlank()) {
            return TokenValidationResult.invalid("missing_issuer");
        }
        try {
            JWKSource<SecurityContext> keySource = jwkSources.computeIfAbsent(constraints.jwksUri(), this::remoteJwkSource);
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
            JWTClaimsSet claims = processor.process(rawToken, null);
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                return TokenValidationResult.invalid("expired");
            }
            if (!constraints.issuer().equals(claims.getIssuer())) {
                return TokenValidationResult.invalid("invalid_issuer");
            }
            if (!audienceMatches(constraints.audience(), claims)) {
                return TokenValidationResult.invalid("invalid_audience");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return TokenValidationResult.invalid("missing_subject");
            }
            String username = firstNonBlank(
                stringClaim(claims, "preferred_username"),
                stringClaim(claims, "username"),
                stringClaim(claims, "email")
            );
            Identity identity = new Identity(
                subject,
                username,
                stringClaim(claims, "email"),
                realmRoles(claims),
                Set.of(),
                Map.of()
            );
            return TokenValidationResult.valid(identity);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return TokenValidationResult.invalid("invalid_jwks_uri");
        } catch (BadJOSEException | JOSEException | ParseException ex) {
            return TokenValidationResult.invalid("invalid_signature");
        }
    }

    private JWKSource<SecurityContext> remoteJwkSource(final String jwksUri) {
        try {
            return new RemoteJWKSet<>(URI.create(jwksUri).toURL());
        } catch (MalformedURLException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid JWKS URI: " + jwksUri, ex);
        }
    }

    private static boolean audienceMatches(final String expected, final JWTClaimsSet claims) throws ParseException {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        List<String> audience = claims.getAudience();
        if (audience != null && audience.contains(expected)) {
            return true;
        }
        return expected.equals(stringClaim(claims, "azp"));
    }

    private static Set<String> realmRoles(final JWTClaimsSet claims) {
        try {
            Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
            if (realmAccess == null) {
                return Set.of();
            }
            Object roles = realmAccess.get("roles");
            if (!(roles instanceof List<?> list)) {
                return Set.of();
            }
            Set<String> values = new LinkedHashSet<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String role = item.toString();
                if (!role.isBlank() && !IGNORED_IDP_ROLES.contains(role) && !role.startsWith("default-roles-")) {
                    values.add(role);
                }
            }
            return values;
        } catch (ParseException ex) {
            return Set.of();
        }
    }

    private static String firstNonBlank(final String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Duration resolveTtl(final TokenIssueOptions options) {
        if (options != null && options.timeToLive() != null && !options.timeToLive().isZero()
            && !options.timeToLive().isNegative()) {
            return options.timeToLive();
        }
        String configured = properties.getAuth().getJwt().getTimeToLive();
        if (configured == null || configured.isBlank()) {
            return Duration.ofHours(8);
        }
        return Duration.parse(configured);
    }

    private String issuer() {
        return properties.getAuth().getJwt().getIssuer();
    }

    private byte[] secretBytes() {
        String secret = properties.getAuth().getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("porto.api.auth.jwt.secret is required");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "porto.api.auth.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes"
            );
        }
        return bytes;
    }

    private static String stringClaim(final JWTClaimsSet claims, final String name) {
        Object value = claims.getClaim(name);
        return value == null ? null : String.valueOf(value);
    }

    private static Set<String> stringSetClaim(final JWTClaimsSet claims, final String name) {
        Object value = claims.getClaim(name);
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                values.add(item.toString());
            }
        }
        return values;
    }
}
