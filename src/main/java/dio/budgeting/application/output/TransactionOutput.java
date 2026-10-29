package dio.budgeting.application.output;

import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionOutput(
        String id, String description, String category, BigDecimal amount, String currency, Instant createdAt) {
    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                transaction.getAmount().value(),
                Money.CURRENCY,
                transaction.getCreatedAt());
    }
}
