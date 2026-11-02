package dio.budgeting.support.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dio.budgeting.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs real tokens with the secret of the {@code test} profile (application-test.properties). */
public final class AccessTokens {
    public static final String TEST_SECRET = "test-jwt-secret-with-at-least-32-bytes";
    public static final UserId DEFAULT_USER = new UserId(UUID.fromString("0b7e2f0a-1c3d-4e5f-8a9b-0c1d2e3f4a5b"));

    private AccessTokens() {}

    public static String authorization() {
        return authorization(DEFAULT_USER);
    }

    public static String authorization(UserId userId) {
        return "Bearer " + validFor(userId);
    }

    public static String validFor(UserId userId) {
        return sign(TEST_SECRET, validClaims(userId));
    }

    public static JwtClaimsSet.Builder validClaims(UserId userId) {
        return JwtClaimsSet.builder()
                .subject(userId.uuid().toString())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    public static String sign(String secret, JwtClaimsSet.Builder claims) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
