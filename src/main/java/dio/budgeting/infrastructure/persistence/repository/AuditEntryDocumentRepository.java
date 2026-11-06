package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.document.AuditEntryDocument;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEntryDocumentRepository extends MongoRepository<AuditEntryDocument, UUID> {}
