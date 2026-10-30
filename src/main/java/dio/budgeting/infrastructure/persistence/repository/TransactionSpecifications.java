package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.TransactionCriterion;
import dio.budgeting.domain.TransactionCriterion.CategoryIs;
import dio.budgeting.domain.TransactionFilter;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.domain.Specification;

final class TransactionSpecifications {

    private TransactionSpecifications() {}

    static Specification<TransactionEntity> from(TransactionFilter filter) {
        return Specification.allOf(
                filter.criteria().stream().map(TransactionSpecifications::from).toList());
    }

    private static Specification<TransactionEntity> from(TransactionCriterion criterion) {
        return switch (criterion) {
            case CategoryIs categoryIs ->
                (root, query, builder) -> builder.equal(root.get("category"), categoryIs.category());
        };
    }
}
