package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistTransactionUseCase {
    private static final Logger log = LoggerFactory.getLogger(PersistTransactionUseCase.class);

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public PersistTransactionUseCase(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public TransactionOutput execute(PersistTransactionInput input) {
        var transaction = transactionRepository.save(
                new Transaction(input.description(), input.toMoney(), input.category(), Instant.now(clock)));
        log.atInfo().addKeyValue("transactionId", transaction.getId().uuid()).log("Transaction persisted");

        return TransactionOutput.from(transaction);
    }
}
