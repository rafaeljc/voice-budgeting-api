package dio.budgeting.domain.event;

import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.UserAgent;
import dio.budgeting.domain.UserId;
import java.time.Instant;
import java.util.Optional;

public record TransactionProposalCreatedEvent(
        UserId userId,
        Optional<UserAgent> userAgent,
        Instant occurredAt,
        AudioMetadata audio,
        String transcription,
        TransactionProposal proposal)
        implements DomainEvent {}
