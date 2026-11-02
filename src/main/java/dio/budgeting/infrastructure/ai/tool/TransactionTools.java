package dio.budgeting.infrastructure.ai.tool;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.argument.PersistTransactionToolArguments;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class TransactionTools {
    public static final String USER_ID = "userId";

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    public TransactionTools(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput persistTransaction(
            @Valid PersistTransactionToolArguments arguments, ToolContext toolContext) {
        return persistTransactionUseCase.execute(userId(toolContext), arguments.toInput());
    }

    @Tool(name = "list-transactions-by-category", description = "Lista transações financeiras por categoria")
    public List<TransactionOutput> listTransactionsByCategory(
            @ToolParam(description = "Categoria de uma transação") Category category, ToolContext toolContext) {
        return listTransactionsByCategoryUseCase.execute(userId(toolContext), category);
    }

    // The user comes from the authenticated request, never from the model, so it is not part of the tool schema
    private static UserId userId(ToolContext toolContext) {
        return (UserId) toolContext.getContext().get(USER_ID);
    }
}
