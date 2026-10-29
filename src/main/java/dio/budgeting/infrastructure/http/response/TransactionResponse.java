package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;
import java.time.Instant;

public record TransactionResponse(
        String id, String category, String description, String amount, String currency, Instant createdAt) {
    public static TransactionResponse from(TransactionOutput output) {
        return new TransactionResponse(
                output.id(),
                output.category(),
                output.description(),
                output.amount().toPlainString(),
                output.currency(),
                output.createdAt());
    }
}
