package dio.budgeting.application.output;

import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionProposal;
import java.math.BigDecimal;

public record TransactionProposalOutput(
        String id, String description, String category, BigDecimal amount, String currency) {
    public static TransactionProposalOutput from(TransactionProposal proposal) {
        return new TransactionProposalOutput(
                proposal.getId().uuid().toString(),
                proposal.getDescription(),
                proposal.getCategory().name(),
                proposal.getAmount().value(),
                Money.CURRENCY);
    }
}
