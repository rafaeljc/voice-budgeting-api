package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.document.AiUsageEntryDocument;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiUsageEntryDocumentRepository extends MongoRepository<AiUsageEntryDocument, UUID> {}
