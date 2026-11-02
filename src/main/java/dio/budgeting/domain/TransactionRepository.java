package dio.budgeting.domain;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(TransactionId id, UserId userId);

    PageResult<Transaction> findAllByUserId(
            UserId userId, TransactionFilter filter, TransactionSort sort, PageQuery page);

    List<Transaction> findAllByUserIdAndCategory(UserId userId, Category category);
}
