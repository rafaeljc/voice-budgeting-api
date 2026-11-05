package dio.budgeting.domain;

import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(TransactionId id, UserId userId);

    PageResult<Transaction> findAllByUserId(
            UserId userId, TransactionFilter filter, TransactionSort sort, PageQuery page);

    TransactionSummary summarizeByUserIdAndCategory(UserId userId, Category category);
}
