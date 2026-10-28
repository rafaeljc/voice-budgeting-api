package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class ListTransactionsByCategoryUseCase {
    private static final Logger log = LoggerFactory.getLogger(ListTransactionsByCategoryUseCase.class);

    private final TransactionRepository transactionRepository;

    public ListTransactionsByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "list-transactions-by-category", description = "Lista transações financeiras por categoria")
    public List<TransactionOutput> execute(@ToolParam(description = "Categoria de uma transação") Category category) {
        var transactions = transactionRepository.findAllByCategory(category).stream()
                .map(TransactionOutput::from)
                .toList();
        log.atInfo().addKeyValue("resultCount", transactions.size()).log("Transactions listed");
        return transactions;
    }
}
