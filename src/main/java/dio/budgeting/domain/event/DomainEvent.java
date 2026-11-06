package dio.budgeting.domain.event;

import dio.budgeting.domain.UserId;
import java.time.Instant;

public sealed interface DomainEvent
        permits TransactionProposalCreatedEvent,
                VoiceCommandNotUnderstoodEvent,
                TransactionProposalConfirmedEvent,
                TransactionProposalCanceledEvent {

    DomainEventId id();

    UserId userId();

    Instant occurredAt();
}
