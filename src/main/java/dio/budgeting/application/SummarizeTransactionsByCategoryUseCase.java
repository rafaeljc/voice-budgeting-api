package dio.budgeting.application;

import dio.budgeting.application.output.TransactionSummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummarizeTransactionsByCategoryUseCase {
    private static final Logger log = LoggerFactory.getLogger(SummarizeTransactionsByCategoryUseCase.class);

    private final TransactionRepository transactionRepository;

    public SummarizeTransactionsByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionSummaryOutput execute(UserId userId, Category category) {
        var summary = transactionRepository.summarizeByUserIdAndCategory(userId, category);
        log.atInfo().addKeyValue("resultCount", summary.count()).log("Transactions summarized");
        return TransactionSummaryOutput.from(category, summary);
    }
}
