package dio.budgeting.infrastructure.event;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import dio.budgeting.application.AppendAuditEntryUseCase;
import dio.budgeting.domain.event.DomainEvent;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DomainEventAuditListener {
    private static final Logger log = LoggerFactory.getLogger(DomainEventAuditListener.class);

    private final AppendAuditEntryUseCase appendAuditEntry;

    public DomainEventAuditListener(AppendAuditEntryUseCase appendAuditEntry) {
        this.appendAuditEntry = appendAuditEntry;
    }

    // Async: the audit never holds the request. AFTER_COMMIT: a decision that rolls back never becomes an audit entry,
    // which a plain @EventListener would queue before the commit. fallbackExecution: the voice command has no
    // transaction and records right away. The failed future hands the failure to the event publication registry, which
    // marks the publication as failed for resubmission; a thrown exception from a void @Async would instead end up in
    // the default handler as an ERROR with a raw stack trace
    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public CompletableFuture<Void> on(DomainEvent event) {
        try {
            appendAuditEntry.execute(event);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            // Only types, never the event content or the exception message: both can carry business data
            log.atWarn()
                    .addKeyValue("eventType", event.getClass().getSimpleName())
                    .addKeyValue("errorType", e.getClass().getSimpleName())
                    .log("Audit entry not appended");
            return CompletableFuture.failedFuture(e);
        }
    }
}
