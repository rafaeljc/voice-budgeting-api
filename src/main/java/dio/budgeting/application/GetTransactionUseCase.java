package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionNotFoundException;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTransactionUseCase {
    private final TransactionRepository transactionRepository;

    public GetTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionOutput execute(UserId userId, TransactionId id) {
        // An unknown ID and another user's transaction end the same way: not found
        return transactionRepository
                .findByIdAndUserId(id, userId)
                .map(TransactionOutput::from)
                .orElseThrow(TransactionNotFoundException::new);
    }
}
