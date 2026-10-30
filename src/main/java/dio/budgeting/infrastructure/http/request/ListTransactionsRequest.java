package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.ListTransactionsInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.PageQuery;
import dio.budgeting.domain.TransactionCriterion.CategoryIs;
import dio.budgeting.domain.TransactionFilter;
import dio.budgeting.domain.TransactionSort;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record ListTransactionsRequest(
        Category category,
        Integer page,
        Integer size,

        @Pattern(regexp = "^(createdAt|amount),(asc|desc)$", message = "{transaction.sort.invalid}")
        String sort) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public ListTransactionsInput toInput() {
        var filter = category == null ? TransactionFilter.of() : TransactionFilter.of(new CategoryIs(category));
        return new ListTransactionsInput(filter, toSort(), new PageQuery(pageNumber(), pageSize()));
    }

    // Out-of-range numbers are adjusted instead of rejected, as Spring Data does
    private int pageNumber() {
        return page == null ? 0 : Math.max(page, 0);
    }

    private int pageSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private TransactionSort toSort() {
        if (sort == null) {
            return TransactionSort.DEFAULT;
        }
        var parts = sort.split(",");
        var field = parts[0].equals("amount") ? TransactionSort.Field.AMOUNT : TransactionSort.Field.CREATED_AT;
        var direction = TransactionSort.Direction.valueOf(parts[1].toUpperCase(Locale.ROOT));
        return new TransactionSort(field, direction);
    }
}
