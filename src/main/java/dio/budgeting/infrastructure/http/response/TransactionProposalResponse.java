package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionProposalOutput;

public record TransactionProposalResponse(
        String id, String description, String amount, String currency, String category) {
    public static TransactionProposalResponse from(TransactionProposalOutput output) {
        return new TransactionProposalResponse(
                output.id(),
                output.description(),
                output.amount().toPlainString(),
                output.currency(),
                output.category());
    }
}
