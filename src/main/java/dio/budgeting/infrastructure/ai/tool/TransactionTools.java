package dio.budgeting.infrastructure.ai.tool;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.argument.ProposeTransactionToolArguments;
import dio.budgeting.infrastructure.ai.tool.result.ProposeTransactionToolResult;
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
    public static final String PROPOSAL_COLLECTOR = "proposalCollector";

    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    public TransactionTools(ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase) {
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
    }

    @Tool(
            name = "propose-transaction",
            description = "Propõe o registro de uma nova transação financeira, que só vale depois da confirmação"
                    + " do usuário")
    public ProposeTransactionToolResult proposeTransaction(
            @Valid ProposeTransactionToolArguments arguments, ToolContext toolContext) {
        var proposal = collector(toolContext).collect(arguments.toInput());
        return ProposeTransactionToolResult.awaitingConfirmation(proposal);
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

    private static TransactionProposalCollector collector(ToolContext toolContext) {
        return (TransactionProposalCollector) toolContext.getContext().get(PROPOSAL_COLLECTOR);
    }
}
