package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TransactionEntityRepository
        extends CrudRepository<TransactionEntity, UUID>, JpaSpecificationExecutor<TransactionEntity> {
    Optional<TransactionEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select new dio.budgeting.infrastructure.persistence.repository.TransactionTotals(count(t), sum(t.amount))
            from TransactionEntity t
            where t.userId = :userId and t.category = :category
            """)
    TransactionTotals summarizeByUserIdAndCategory(UUID userId, Category category);

    List<TransactionEntity> findAllByUserIdAndCategory(UUID userId, Category category);
}
