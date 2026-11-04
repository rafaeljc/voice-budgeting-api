package dio.budgeting.application;

import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalNotFoundException;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelTransactionProposalUseCase {
    private static final Logger log = LoggerFactory.getLogger(CancelTransactionProposalUseCase.class);

    private final TransactionProposalRepository proposalRepository;

    public CancelTransactionProposalUseCase(TransactionProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @Transactional
    public void execute(UserId userId, TransactionProposalId id) {
        // Locked read: a concurrent decision on the same proposal waits for this transaction and then finds nothing.
        // An unknown ID and another user's proposal end the same way: not found
        var proposal = proposalRepository
                .findByIdAndUserIdForUpdate(id, userId)
                .orElseThrow(TransactionProposalNotFoundException::new);

        proposalRepository.delete(proposal);
        log.atInfo()
                .addKeyValue("transactionProposalId", proposal.getId().uuid())
                .log("Transaction proposal canceled");
    }
}
