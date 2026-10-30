package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionNotFoundException;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTransactionUseCase {
    private final TransactionRepository transactionRepository;

    public GetTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionOutput execute(String id) {
        // A malformed ID names no transaction, so it is the same outcome as an unknown one
        return TransactionId.parse(id)
                .flatMap(transactionRepository::findById)
                .map(TransactionOutput::from)
                .orElseThrow(TransactionNotFoundException::new);
    }
}
