package dio.budgeting.infrastructure.scheduling;

import dio.budgeting.application.DeleteExpiredTransactionProposalBatchUseCase;
import dio.budgeting.infrastructure.config.TransactionProposalProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
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
