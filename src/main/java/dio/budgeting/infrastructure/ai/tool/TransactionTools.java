package dio.budgeting.infrastructure.ai.tool;

import dio.budgeting.application.SummarizeTransactionsByCategoryUseCase;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.argument.ProposeTransactionToolArguments;
import jakarta.validation.Valid;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** The model only extracts the intent: nothing a tool does returns to it, since `returnDirect` ends the call. */
@Component
@Validated
public class TransactionTools {
    public static final String USER_ID = "userId";
    public static final String INTERPRETATION_COLLECTOR = "interpretationCollector";

    private final SummarizeTransactionsByCategoryUseCase summarizeTransactionsByCategoryUseCase;

    public TransactionTools(SummarizeTransactionsByCategoryUseCase summarizeTransactionsByCategoryUseCase) {
        this.summarizeTransactionsByCategoryUseCase = summarizeTransactionsByCategoryUseCase;
    }

    @Tool(
            name = "propose-transaction",
            description = "Propõe o registro de uma nova transação financeira, que só vale depois da confirmação"
                    + " do usuário",
            returnDirect = true)
    public void proposeTransaction(@Valid ProposeTransactionToolArguments arguments, ToolContext toolContext) {
        collector(toolContext).note(new AssistantOutput.Proposal(arguments.toInput()));
    }

    @Tool(
            name = "summarize-transactions-by-category",
            description = "Consulta quantos gastos o usuário tem em uma categoria e quanto eles somam",
            returnDirect = true)
    public void summarizeTransactionsByCategory(
            @ToolParam(description = "Categoria de uma transação") Category category, ToolContext toolContext) {
        var summary = summarizeTransactionsByCategoryUseCase.execute(userId(toolContext), category);
        collector(toolContext).note(new AssistantOutput.Summary(summary));
    }

    // The user comes from the authenticated request, never from the model, so it is not part of the tool schema
    private static UserId userId(ToolContext toolContext) {
        return (UserId) toolContext.getContext().get(USER_ID);
    }

    private static InterpretationCollector collector(ToolContext toolContext) {
        return (InterpretationCollector) toolContext.getContext().get(INTERPRETATION_COLLECTOR);
    }
}
