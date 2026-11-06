package dio.budgeting.domain.event;

import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import java.time.Instant;

public record TransactionProposalConfirmedEvent(
        UserId userId, Instant occurredAt, TransactionProposalId transactionProposalId, TransactionId transactionId)
        implements DomainEvent {}
