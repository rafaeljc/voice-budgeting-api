package dio.budgeting.domain.event;

import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserAgent;
import dio.budgeting.domain.UserId;
import java.time.Instant;
import java.util.Optional;

public record TransactionProposalConfirmedEvent(
        UserId userId,
        Optional<UserAgent> userAgent,
        Instant occurredAt,
        TransactionProposalId transactionProposalId,
        TransactionId transactionId)
        implements DomainEvent {}
