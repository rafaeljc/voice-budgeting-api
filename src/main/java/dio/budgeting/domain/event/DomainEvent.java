package dio.budgeting.domain.event;

import dio.budgeting.domain.UserAgent;
import dio.budgeting.domain.UserId;
import java.time.Instant;
import java.util.Optional;

public sealed interface DomainEvent
        permits TransactionProposalCreatedEvent,
                VoiceCommandNotUnderstoodEvent,
                TransactionProposalConfirmedEvent,
                TransactionProposalCanceledEvent {

    UserId userId();

    Optional<UserAgent> userAgent();

    Instant occurredAt();
}
