package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.PageQuery;
import dio.budgeting.domain.PageResult;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionFilter;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.TransactionSort;
import dio.budgeting.domain.TransactionSummary;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTransactionRepository implements TransactionRepository {
    private final TransactionEntityRepository transactionEntityRepository;

    public JpaTransactionRepository(TransactionEntityRepository transactionEntityRepository) {
        this.transactionEntityRepository = transactionEntityRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var entity = TransactionEntity.from(transaction);
        return transactionEntityRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Transaction> findByIdAndUserId(TransactionId id, UserId userId) {
        return transactionEntityRepository
                .findByIdAndUserId(id.uuid(), userId.uuid())
                .map(entity -> entity.toDomain());
    }

    @Override
    public PageResult<Transaction> findAllByUserId(
            UserId userId, TransactionFilter filter, TransactionSort sort, PageQuery page) {
        var pageable = PageRequest.of(page.number(), page.size(), toSpringSort(sort));
        var result = transactionEntityRepository.findAll(
                TransactionSpecifications.ownedBy(userId).and(TransactionSpecifications.from(filter)), pageable);
        var content =
                result.getContent().stream().map(entity -> entity.toDomain()).toList();
        return new PageResult<>(content, page.number(), page.size(), result.getTotalElements());
    }

    // The id breaks ties so that pages never repeat or skip rows sharing the same sort value
    private static Sort toSpringSort(TransactionSort sort) {
        var direction = Sort.Direction.valueOf(sort.direction().name());
        var property = sort.field() == TransactionSort.Field.AMOUNT ? "amount" : "createdAt";
        return Sort.by(direction, property).and(Sort.by(direction, "id"));
    }

    @Override
    public TransactionSummary summarizeByUserIdAndCategory(UserId userId, Category category) {
        var totals = transactionEntityRepository.summarizeByUserIdAndCategory(userId.uuid(), category);
        var total = totals.total() == null ? BigDecimal.ZERO : totals.total();
        return new TransactionSummary(totals.count(), Money.of(total));
    }

    @Override
    public List<Transaction> findAllByUserIdAndCategory(UserId userId, Category category) {
        return transactionEntityRepository.findAllByUserIdAndCategory(userId.uuid(), category).stream()
                .map(entity -> entity.toDomain())
                .toList();
    }
}
