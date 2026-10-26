package dio.budgeting.support.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@IntegrationTest
class MySqlCleanerIT {

    private final TransactionEntityRepository repository;
    private final MySqlCleaner cleaner;

    MySqlCleanerIT(TransactionEntityRepository repository, MySqlCleaner cleaner) {
        this.repository = repository;
        this.cleaner = cleaner;
    }

    @Test
    void should_removeAllRows_when_cleaned() {
        repository.save(new TransactionEntity(UUID.randomUUID(), "Mercado", 8000, Category.GROCERIES));
        assertThat(repository.count()).isEqualTo(1);

        cleaner.clean();

        assertThat(repository.count()).isZero();
    }
}
