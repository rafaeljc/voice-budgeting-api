package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.AuditEntryRepository;
import dio.budgeting.domain.event.DomainEvent;
import dio.budgeting.infrastructure.persistence.document.AuditEntryDocument;
import io.micrometer.tracing.Tracer;
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
        documentRepository.insert(AuditEntryDocument.from(event, traceId()));
    }

    // The tracing context reaches the async thread by context propagation; without a span there is no trace to link
    private String traceId() {
        var span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
