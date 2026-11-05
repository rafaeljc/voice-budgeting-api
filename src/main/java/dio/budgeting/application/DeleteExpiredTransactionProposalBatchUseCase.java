package dio.budgeting.application;

import dio.budgeting.domain.TransactionProposalRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteExpiredTransactionProposalBatchUseCase {
    private final TransactionProposalRepository proposalRepository;

    public DeleteExpiredTransactionProposalBatchUseCase(TransactionProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @Transactional
    public int execute(Instant expiredAt, int batchSize) {
        return proposalRepository.deleteExpiredBatch(expiredAt, batchSize);
    }
}
