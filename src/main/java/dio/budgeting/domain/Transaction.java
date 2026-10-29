package dio.budgeting.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Transaction {
    private TransactionId id;
    private String description;
    private Money amount;
    private Category category;
    private Instant createdAt;

    public Transaction(String description, Money amount, Category category, Instant createdAt) {
        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
    }
}
