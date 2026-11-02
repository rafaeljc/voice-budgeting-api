package dio.budgeting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@IntegrationTest
class JpaTransactionRepositoryIT {
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45Z");

    private final TransactionRepository repository;

    JpaTransactionRepositoryIT(TransactionRepository repository) {
        this.repository = repository;
    }

    @Test
    void should_returnOnlyOwnTransactions_when_listingByCategory() {
        // given
        var owner = new UserId(UUID.randomUUID());
        var other = new UserId(UUID.randomUUID());
        var own = repository.save(transaction(owner, "Mercado", Category.GROCERIES));
        repository.save(transaction(owner, "Remédio", Category.PHARMA));
        repository.save(transaction(other, "Feira", Category.GROCERIES));

        // when
        var found = repository.findAllByUserIdAndCategory(owner, Category.GROCERIES);

        // then
        assertThat(found).extracting(transaction -> transaction.getId()).containsExactly(own.getId());
    }

    private static Transaction transaction(UserId userId, String description, Category category) {
        return new Transaction(userId, description, new Money(new BigDecimal("10.00")), category, NOW);
    }
}
