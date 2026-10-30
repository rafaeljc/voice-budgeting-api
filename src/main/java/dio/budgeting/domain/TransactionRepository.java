package dio.budgeting.domain;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction);

    Optional<Transaction> findById(TransactionId id);

    PageResult<Transaction> findAll(TransactionFilter filter, TransactionSort sort, PageQuery page);

    List<Transaction> findAllByCategory(Category category);
}
