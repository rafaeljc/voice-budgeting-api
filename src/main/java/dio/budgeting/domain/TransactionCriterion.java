package dio.budgeting.domain;

/** A condition a transaction must satisfy to match a search; each new way of filtering is a new permitted record. */
public sealed interface TransactionCriterion {
    record CategoryIs(Category category) implements TransactionCriterion {
        public CategoryIs {
            if (category == null) {
                throw new IllegalArgumentException("Category must not be null");
            }
        }
    }
}
