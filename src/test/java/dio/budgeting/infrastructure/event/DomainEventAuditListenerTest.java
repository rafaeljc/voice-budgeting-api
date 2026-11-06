package dio.budgeting.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import dio.budgeting.application.AppendAuditEntryUseCase;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.event.DomainEventId;
import dio.budgeting.domain.event.TransactionProposalCanceledEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DomainEventAuditListenerTest {
    private static final TransactionProposalCanceledEvent EVENT = new TransactionProposalCanceledEvent(
            new DomainEventId(),
            new UserId(UUID.randomUUID()),
            Instant.parse("2026-03-01T12:35:00Z"),
            new TransactionProposalId());

    @Mock
    private AppendAuditEntryUseCase appendAuditEntry;

    @InjectMocks
    private DomainEventAuditListener listener;

    @Test
    void should_appendAuditEntry_when_eventIsPublished() {
        // when
        var result = listener.on(EVENT);

        // then
        verify(appendAuditEntry).execute(EVENT);
        assertThat(result).isCompleted();
    }

    @Test
    void should_completeFutureExceptionally_when_appendFails() {
        // given
        var failure = new IllegalStateException("mongo down");
        doThrow(failure).when(appendAuditEntry).execute(EVENT);

        // when
        var result = listener.on(EVENT);

        // then
        assertThat(result)
                .isCompletedExceptionally()
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCause(failure);
    }
}
