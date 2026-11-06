package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.AuditEntryRepository;
import dio.budgeting.domain.event.DomainEvent;
import dio.budgeting.infrastructure.persistence.document.AuditEntryDocument;
import io.micrometer.tracing.Tracer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MongoAuditEntryRepository implements AuditEntryRepository {
    private final AuditEntryDocumentRepository documentRepository;
    private final Tracer tracer;

    public MongoAuditEntryRepository(AuditEntryDocumentRepository documentRepository, Tracer tracer) {
        this.documentRepository = documentRepository;
        this.tracer = tracer;
    }

    @Override
    public void append(DomainEvent event) {
        try {
            documentRepository.insert(AuditEntryDocument.from(event, traceId()));
        } catch (DuplicateKeyException alreadyAppended) {
            // Delivery is at least once and the entry id is the event id: a duplicate key means this event was
            // already appended (a resubmission), so there is nothing left to do
        }
    }

    // The tracing context reaches the async thread by context propagation; without a span there is no trace to link
    private String traceId() {
        var span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
