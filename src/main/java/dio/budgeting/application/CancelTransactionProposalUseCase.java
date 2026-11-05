package dio.budgeting.application;

import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalNotFoundException;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelTransactionProposalUseCase {
    private static final Logger log = LoggerFactory.getLogger(CancelTransactionProposalUseCase.class);

    private final TransactionProposalRepository proposalRepository;
    private final Clock clock;

    public CancelTransactionProposalUseCase(TransactionProposalRepository proposalRepository, Clock clock) {
        this.proposalRepository = proposalRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(UserId userId, TransactionProposalId id) {
        // Locked read: a concurrent decision on the same proposal waits for this transaction and then finds nothing.
        // An unknown ID, another user's proposal and an expired one end the same way: not found. The expired row is
        // left for the sweep
        var now = Instant.now(clock);
        var proposal = proposalRepository
                .findByIdAndUserIdForUpdate(id, userId)
                .filter(found -> found.isPendingAt(now))
                .orElseThrow(TransactionProposalNotFoundException::new);

        proposalRepository.delete(proposal);
        log.atInfo()
                .addKeyValue("transactionProposalId", proposal.getId().uuid())
                .log("Transaction proposal canceled");
    }
}
