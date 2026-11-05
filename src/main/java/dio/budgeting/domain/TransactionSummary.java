package dio.budgeting.domain;

public record TransactionSummary(long count, Money total) {
    public TransactionSummary {
        if (count < 0) {
            throw new IllegalArgumentException("Transaction count must not be negative");
        }
        if (total == null) {
            throw new IllegalArgumentException("Transaction total must not be null");
        }
    }
}
