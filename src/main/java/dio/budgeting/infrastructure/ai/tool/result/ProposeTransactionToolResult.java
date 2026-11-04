package dio.budgeting.infrastructure.ai.tool.result;

import dio.budgeting.application.input.ProposeTransactionInput;
import java.math.BigDecimal;

/** What the model learns about its proposal: no ID, because nothing was recorded yet. */
public record ProposeTransactionToolResult(String description, BigDecimal amount, String category, String status) {
    private static final String AWAITING_CONFIRMATION = "aguardando confirmação do usuário";

    public static ProposeTransactionToolResult awaitingConfirmation(ProposeTransactionInput proposal) {
        return new ProposeTransactionToolResult(
                proposal.description().strip(),
                proposal.toMoney().value(),
                proposal.category().name(),
                AWAITING_CONFIRMATION);
    }
}
