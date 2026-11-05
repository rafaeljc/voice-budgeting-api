package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalNotFoundException;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmTransactionProposalUseCase {
    private static final Logger log = LoggerFactory.getLogger(ConfirmTransactionProposalUseCase.class);

    private final TransactionProposalRepository proposalRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public ConfirmTransactionProposalUseCase(
            TransactionProposalRepository proposalRepository,
            TransactionRepository transactionRepository,
            Clock clock) {
        this.proposalRepository = proposalRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public TransactionOutput execute(UserId userId, TransactionProposalId id) {
        // Locked read: a concurrent decision on the same proposal waits for this transaction and then finds nothing.
        // An unknown ID, another user's proposal and an expired one end the same way: not found. The expired row is
        // left for the sweep
        var now = Instant.now(clock);
        var proposal = proposalRepository
                .findByIdAndUserIdForUpdate(id, userId)
                .filter(found -> found.isPendingAt(now))
                .orElseThrow(TransactionProposalNotFoundException::new);

        var transaction = transactionRepository.save(proposal.toTransaction(now));
        proposalRepository.delete(proposal);
        log.atInfo()
                .addKeyValue("transactionProposalId", proposal.getId().uuid())
                .addKeyValue("transactionId", transaction.getId().uuid())
                .log("Transaction proposal confirmed");

        return TransactionOutput.from(transaction);
    }
}
