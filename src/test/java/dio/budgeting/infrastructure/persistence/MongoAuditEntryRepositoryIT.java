package dio.budgeting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dio.budgeting.domain.AuditEntryRepository;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.event.DomainEventId;
import dio.budgeting.domain.event.TransactionProposalCanceledEvent;
import dio.budgeting.infrastructure.persistence.document.AuditEntryDocument;
import dio.budgeting.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

@IntegrationTest
class MongoAuditEntryRepositoryIT {
    private final AuditEntryRepository repository;
    private final MongoTemplate mongoTemplate;

    MongoAuditEntryRepositoryIT(AuditEntryRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Test
    void should_keepSingleEntryWithEventIdAsId_when_sameEventIsAppendedTwice() {
        // given
        var event = new TransactionProposalCanceledEvent(
                new DomainEventId(),
                new UserId(UUID.randomUUID()),
                Instant.parse("2026-03-01T12:35:00Z"),
                new TransactionProposalId());

        // when
        repository.append(event);

        // then
        assertThatCode(() -> repository.append(event)).doesNotThrowAnyException();
        var entries = mongoTemplate.findAll(AuditEntryDocument.class);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getId()).isEqualTo(event.id().uuid());
    }
}
