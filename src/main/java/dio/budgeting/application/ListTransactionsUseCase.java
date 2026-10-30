package dio.budgeting.application;

import dio.budgeting.application.input.ListTransactionsInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.PageResult;
import dio.budgeting.domain.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTransactionsUseCase {
    private static final Logger log = LoggerFactory.getLogger(ListTransactionsUseCase.class);

    private final TransactionRepository transactionRepository;

    public ListTransactionsUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<TransactionOutput> execute(ListTransactionsInput input) {
        var page = transactionRepository
                .findAll(input.filter(), input.sort(), input.page())
                .map(TransactionOutput::from);
        log.atInfo().addKeyValue("resultCount", page.content().size()).log("Transactions listed");
        return page;
    }
}
