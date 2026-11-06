package dio.budgeting.domain.event;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import java.time.Instant;

public record TransactionProposalCreatedEvent(
        DomainEventId id,
        UserId userId,
        Instant occurredAt,
        AudioMetadata audio,
        String transcription,
        TransactionProposalId transactionProposalId,
        String description,
        Money amount,
        Category category,
        Instant expiresAt)
        implements DomainEvent {}
