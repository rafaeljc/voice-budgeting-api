package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface TransactionProposalEntityRepository extends JpaRepository<TransactionProposalEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransactionProposalEntity> findByIdAndUserId(UUID id, UUID userId);

    // A decision locks its row even when the proposal is expired, so the sweep skips locked rows (-2 is Hibernate's
    // SKIP LOCKED) instead of waiting for them; they are deleted on the next run
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<TransactionProposalEntity> findByExpiresAtLessThanEqualOrderByExpiresAt(Instant expiredAt, Limit limit);
}
