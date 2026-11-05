package dio.budgeting.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("budgeting.transaction-proposals")
public record TransactionProposalProperties(
        @NotNull Duration timeToLive,
        @NotNull Duration expirationSweepInterval,
        @Positive int expirationBatchSize,
        boolean expirationSweepEnabled) {}
