package dio.budgeting.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionProposal {
    private TransactionProposalId id;
    private UserId userId;
    private String description;
    private Money amount;
    private Category category;
    private Instant createdAt;

    public TransactionProposal(UserId userId, String description, Money amount, Category category, Instant createdAt) {
        requireValid(userId, description, amount, category, createdAt);
        this.id = new TransactionProposalId();
        this.userId = userId;
        this.description = description.strip();
        this.amount = amount;
        this.category = category;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public Transaction toTransaction(Instant confirmedAt) {
        return new Transaction(userId, description, amount, category, confirmedAt);
    }

    private static void requireValid(
            UserId userId, String description, Money amount, Category category, Instant createdAt) {
        if (userId == null) {
            throw new IllegalArgumentException("TransactionProposal userId must not be null");
        }
        if (description == null || description.strip().isEmpty()) {
            throw new IllegalArgumentException("TransactionProposal description must not be blank");
        }
        if (description.strip().length() > Transaction.DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("TransactionProposal description must have at most "
                    + Transaction.DESCRIPTION_MAX_LENGTH + " characters");
        }
        if (amount == null || amount.value().signum() <= 0) {
            throw new IllegalArgumentException("TransactionProposal amount must be greater than zero");
        }
        if (category == null) {
            throw new IllegalArgumentException("TransactionProposal category must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("TransactionProposal createdAt must not be null");
        }
    }
}
