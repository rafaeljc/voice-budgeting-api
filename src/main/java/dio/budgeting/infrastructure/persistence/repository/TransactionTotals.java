package dio.budgeting.infrastructure.persistence.repository;

import java.math.BigDecimal;

// SUM over no rows is null, and its scale depends on the database
public record TransactionTotals(long count, BigDecimal total) {}
