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
    void should_countAndSumOnlyOwnTransactionsOfCategory_when_summarizing() {
        // given
        var owner = new UserId(UUID.randomUUID());
        var other = new UserId(UUID.randomUUID());
        repository.save(transaction(owner, "Mercado", Category.GROCERIES, "80.00"));
        repository.save(transaction(owner, "Feira", Category.GROCERIES, "160.50"));
        repository.save(transaction(owner, "Remédio", Category.PHARMA, "30.00"));
        repository.save(transaction(other, "Padaria", Category.GROCERIES, "500.00"));

        // when
        var summary = repository.summarizeByUserIdAndCategory(owner, Category.GROCERIES);

        // then
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.total()).isEqualTo(new Money(new BigDecimal("240.50")));
    }

    @Test
    void should_returnZeroCountAndTotal_when_noTransactionsInCategory() {
        // given
        var owner = new UserId(UUID.randomUUID());
        repository.save(transaction(owner, "Remédio", Category.PHARMA, "30.00"));

        // when
        var summary = repository.summarizeByUserIdAndCategory(owner, Category.GROCERIES);

        // then
        assertThat(summary.count()).isZero();
        assertThat(summary.total()).isEqualTo(new Money(new BigDecimal("0.00")));
    }

    private static Transaction transaction(UserId userId, String description, Category category) {
        return transaction(userId, description, category, "10.00");
    }

    private static Transaction transaction(UserId userId, String description, Category category, String amount) {
        return new Transaction(userId, description, new Money(new BigDecimal(amount)), category, NOW);
    }
}
