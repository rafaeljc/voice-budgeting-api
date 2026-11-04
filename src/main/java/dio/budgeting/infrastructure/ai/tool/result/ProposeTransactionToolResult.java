package dio.budgeting.infrastructure.ai.tool.result;

import dio.budgeting.application.input.ProposeTransactionInput;
import java.math.BigDecimal;

/** What the model learns about its proposal: no ID, because nothing was recorded yet. */
public record ProposeTransactionToolResult(String description, BigDecimal amount, String category, String message) {
    private static final String PROPOSAL_NOTED = "Proposta de transação anotada com sucesso.";

    public static ProposeTransactionToolResult noted(ProposeTransactionInput proposal) {
        return new ProposeTransactionToolResult(
                proposal.description(),
                proposal.toMoney().value(),
                proposal.category().name(),
                PROPOSAL_NOTED);
    }
}
