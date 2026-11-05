package dio.budgeting.domain;

import java.time.Instant;
import java.util.Optional;

public interface TransactionProposalRepository {
    TransactionProposal save(TransactionProposal proposal);

    Optional<TransactionProposal> findByIdAndUserIdForUpdate(TransactionProposalId id, UserId userId);

    void delete(TransactionProposal proposal);

    int deleteExpiredBatch(Instant expiredAt, int batchSize);
}
