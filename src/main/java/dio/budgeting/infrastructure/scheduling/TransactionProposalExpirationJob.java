package dio.budgeting.infrastructure.scheduling;

import dio.budgeting.application.DeleteExpiredTransactionProposalBatchUseCase;
import dio.budgeting.infrastructure.config.TransactionProposalProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "budgeting.transaction-proposals.expiration-sweep-enabled", matchIfMissing = true)
public class TransactionProposalExpirationJob {
    private static final Logger log = LoggerFactory.getLogger(TransactionProposalExpirationJob.class);

    private final DeleteExpiredTransactionProposalBatchUseCase deleteExpiredBatch;
    private final TransactionProposalProperties properties;
    private final Clock clock;

    public TransactionProposalExpirationJob(
            DeleteExpiredTransactionProposalBatchUseCase deleteExpiredBatch,
            TransactionProposalProperties properties,
            Clock clock) {
        this.deleteExpiredBatch = deleteExpiredBatch;
        this.properties = properties;
        this.clock = clock;
    }

    // Only after the startup succeeds, and only when the sweep is enabled: its absence in the logs is a clue
    @EventListener(ApplicationReadyEvent.class)
    void logSweepSchedule() {
        log.atInfo()
                .addKeyValue("interval", properties.expirationSweepInterval())
                .addKeyValue("batchSize", properties.expirationBatchSize())
                .log("Transaction proposal expiration sweep scheduled");
    }

    // Fixed delay, not fixed rate: the interval counts from the end of the previous run, so a slow run never overlaps
    // the next one on this instance
    @Scheduled(fixedDelayString = "${budgeting.transaction-proposals.expiration-sweep-interval}")
    public void sweep() {
        // One instant for the whole run, so the run ends even if more proposals expire meanwhile
        var expiredAt = clock.instant();
        var batchSize = properties.expirationBatchSize();
        var total = 0;
        int deleted;
        do {
            deleted = deleteExpiredBatch.execute(expiredAt, batchSize);
            total += deleted;
        } while (deleted == batchSize);

        if (total > 0) {
            log.atInfo().addKeyValue("count", total).log("Transaction proposals expired");
        }
    }
}
