package dio.budgeting.domain.event;

import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.UserId;
import java.time.Instant;

public record TransactionProposalCreatedEvent(
        UserId userId, Instant occurredAt, AudioMetadata audio, String transcription, TransactionProposal proposal)
        implements DomainEvent {}
