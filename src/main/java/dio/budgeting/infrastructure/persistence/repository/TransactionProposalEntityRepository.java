package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.CrudRepository;

public interface TransactionProposalEntityRepository extends CrudRepository<TransactionProposalEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransactionProposalEntity> findByIdAndUserId(UUID id, UUID userId);
}
