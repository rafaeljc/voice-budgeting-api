package dio.budgeting.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("budgeting.cache")
public record CachingProperties(
        @Valid @NotNull Policy speech, @Valid @NotNull Policy voiceCommandLocks) {

    public record Policy(@Positive long maxEntries, @NotNull Duration timeToLive) {}
}
