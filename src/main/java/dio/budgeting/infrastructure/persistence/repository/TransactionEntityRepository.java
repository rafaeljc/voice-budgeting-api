package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

public interface TransactionEntityRepository
        extends CrudRepository<TransactionEntity, UUID>, JpaSpecificationExecutor<TransactionEntity> {
    Optional<TransactionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<TransactionEntity> findAllByUserIdAndCategory(UUID userId, Category category);
}
