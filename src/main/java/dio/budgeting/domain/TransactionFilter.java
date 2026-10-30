package dio.budgeting.domain;

import java.util.List;

/** Search criteria for transactions, all of which must hold; no criteria matches every transaction. */
public record TransactionFilter(List<TransactionCriterion> criteria) {
    public TransactionFilter {
        criteria = List.copyOf(criteria);
    }

    public static TransactionFilter of(TransactionCriterion... criteria) {
        return new TransactionFilter(List.of(criteria));
    }
}
