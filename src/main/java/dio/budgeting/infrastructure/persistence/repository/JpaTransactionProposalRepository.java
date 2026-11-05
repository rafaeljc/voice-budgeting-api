package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTransactionProposalRepository implements TransactionProposalRepository {
    private final TransactionProposalEntityRepository proposalEntityRepository;

    public JpaTransactionProposalRepository(TransactionProposalEntityRepository proposalEntityRepository) {
        this.proposalEntityRepository = proposalEntityRepository;
    }

    @Override
    public TransactionProposal save(TransactionProposal proposal) {
        return proposalEntityRepository
                .save(TransactionProposalEntity.from(proposal))
                .toDomain();
    }

    @Override
    public Optional<TransactionProposal> findByIdAndUserIdForUpdate(TransactionProposalId id, UserId userId) {
        return proposalEntityRepository
                .findByIdAndUserId(id.uuid(), userId.uuid())
                .map(entity -> entity.toDomain());
    }

    @Override
    public void delete(TransactionProposal proposal) {
        proposalEntityRepository.deleteById(proposal.getId().uuid());
    }

    @Override
    public int deleteExpiredBatch(Instant expiredAt, int batchSize) {
        var expired =
                proposalEntityRepository.findByExpiresAtLessThanEqualOrderByExpiresAt(expiredAt, Limit.of(batchSize));
        proposalEntityRepository.deleteAllInBatch(expired);
        return expired.size();
    }
}
