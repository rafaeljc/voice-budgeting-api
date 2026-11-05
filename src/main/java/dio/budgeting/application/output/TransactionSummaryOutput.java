package dio.budgeting.application.output;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionSummary;

public record TransactionSummaryOutput(Category category, long count, Money total) {
    public static TransactionSummaryOutput from(Category category, TransactionSummary summary) {
        return new TransactionSummaryOutput(category, summary.count(), summary.total());
    }
}
