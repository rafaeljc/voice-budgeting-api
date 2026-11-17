package dio.budgeting.infrastructure.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("budgeting.rate-limit")
record RateLimitProperties(boolean enabled, @NotNull Map<RateLimitGroup, @Valid @NotNull Limit> groups) {

    RateLimitProperties {
        if (groups != null && !groups.keySet().containsAll(List.of(RateLimitGroup.values()))) {
            throw new IllegalArgumentException("budgeting.rate-limit.groups needs a limit for every group");
        }
    }

    Limit limit(RateLimitGroup group) {
        return groups.get(group);
    }

    record Limit(@Positive int maxRequests, @NotNull Duration period) {}
}
