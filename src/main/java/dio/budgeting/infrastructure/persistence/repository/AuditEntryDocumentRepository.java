package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.document.AuditEntryDocument;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEntryDocumentRepository extends MongoRepository<AuditEntryDocument, ObjectId> {}
