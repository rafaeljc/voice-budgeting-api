package dio.budgeting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionProposalEntityRepository;
import dio.budgeting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class JpaTransactionProposalRepositoryIT {
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.000Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final TransactionProposalRepository repository;
    private final TransactionProposalEntityRepository entityRepository;
    private final TransactionTemplate transactionTemplate;

    JpaTransactionProposalRepositoryIT(
            TransactionProposalRepository repository,
            TransactionProposalEntityRepository entityRepository,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.entityRepository = entityRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // The batch delete runs inside the use case's transaction, so the test opens one too
    @Test
    @Transactional
    void should_deleteExpiredInBatchesAndKeepPending_when_deletingExpiredBatch() {
        // given
        var pending = saveProposal(NOW.plusMillis(1));
        saveProposal(NOW);
        saveProposal(NOW.minusSeconds(1));
        saveProposal(NOW.minusSeconds(2));
        saveProposal(NOW.minusSeconds(3));
        saveProposal(NOW.minusSeconds(4));

        // when
        var first = repository.deleteExpiredBatch(NOW, 2);
        var second = repository.deleteExpiredBatch(NOW, 2);
        var third = repository.deleteExpiredBatch(NOW, 2);
        var fourth = repository.deleteExpiredBatch(NOW, 2);

        // then
        assertThat(new int[] {first, second, third, fourth}).containsExactly(2, 2, 1, 0);
        assertThat(entityRepository.findAll())
                .extracting(proposal -> proposal.getId())
                .containsExactly(pending);
    }

    @Test
    void should_skipRowLockedByAnotherTransaction_when_deletingExpiredBatch() throws Exception {
        // given
        var locked = saveProposal(NOW.minusSeconds(1));
        var free = saveProposal(NOW.minusSeconds(2));

        // when: a decision holds the lock on one expired row while the sweep runs in its own transaction
        var deleted = transactionTemplate.execute(status -> {
            entityRepository.findByIdAndUserId(locked, USER_ID);
            return CompletableFuture.supplyAsync(
                            () -> transactionTemplate.execute(sweepStatus -> repository.deleteExpiredBatch(NOW, 10)))
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();
        });

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(entityRepository.findById(free)).isEmpty();
        assertThat(entityRepository.findById(locked)).isPresent();
    }

    private UUID saveProposal(Instant expiresAt) {
        var id = UUID.randomUUID();
        entityRepository.save(new TransactionProposalEntity(
                id,
                USER_ID,
                "Mercado",
                new BigDecimal("80.00"),
                Category.GROCERIES,
                expiresAt.minusSeconds(300),
                expiresAt));
        return id;
    }
}
