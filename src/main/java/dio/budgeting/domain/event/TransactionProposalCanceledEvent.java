package dio.budgeting.domain.event;

import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import java.time.Instant;

public record TransactionProposalCanceledEvent(
        UserId userId, Instant occurredAt, TransactionProposalId transactionProposalId) implements DomainEvent {}
