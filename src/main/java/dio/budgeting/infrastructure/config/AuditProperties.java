package dio.budgeting.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("budgeting.audit")
public record AuditProperties(
        @NotNull Duration connectTimeout,
        @NotNull Duration serverSelectionTimeout,
        @NotNull Duration readTimeout) {}
