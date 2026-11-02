package dio.budgeting.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("budgeting.security.jwt")
record JwtProperties(
        // HS256 needs a key of at least 256 bits
        @NotBlank @Size(min = 32) String secret) {}
