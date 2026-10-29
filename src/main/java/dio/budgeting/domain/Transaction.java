package dio.budgeting.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Transaction {
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    private TransactionId id;
    private String description;
    private Money amount;
    private Category category;
    private Instant createdAt;

    public Transaction(String description, Money amount, Category category, Instant createdAt) {
        requireValid(description, amount, category, createdAt);
        this.id = new TransactionId();
        this.description = description.strip();
        this.amount = amount;
        this.category = category;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireValid(String description, Money amount, Category category, Instant createdAt) {
        if (description == null || description.strip().isEmpty()) {
            throw new IllegalArgumentException("Transaction description must not be blank");
        }
        if (description.strip().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Transaction description must have at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        if (amount == null || amount.value().signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
        if (category == null) {
            throw new IllegalArgumentException("Transaction category must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Transaction createdAt must not be null");
        }
    }
}
