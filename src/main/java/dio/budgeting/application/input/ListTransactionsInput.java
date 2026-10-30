package dio.budgeting.application.input;

import dio.budgeting.domain.PageQuery;
import dio.budgeting.domain.TransactionFilter;
import dio.budgeting.domain.TransactionSort;

public record ListTransactionsInput(TransactionFilter filter, TransactionSort sort, PageQuery page) {}
