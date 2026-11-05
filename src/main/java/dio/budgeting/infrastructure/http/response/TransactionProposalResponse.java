package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionProposalOutput;
import java.time.Instant;

public record TransactionProposalResponse(
        String id, String description, String amount, String currency, String category, Instant expiresAt) {
    public static TransactionProposalResponse from(TransactionProposalOutput output) {
        return new TransactionProposalResponse(
                output.id(),
                output.description(),
                output.amount().toPlainString(),
                output.currency(),
                output.category(),
                output.expiresAt());
    }
}
