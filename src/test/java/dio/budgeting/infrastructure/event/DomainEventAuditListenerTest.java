package dio.budgeting.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import dio.budgeting.application.AppendAuditEntryUseCase;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.event.DomainEventId;
import dio.budgeting.domain.event.TransactionProposalCanceledEvent;
import java.time.Instant;
import java.util.UUID;
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
        listener.on(EVENT);

        // then
        verify(appendAuditEntry).execute(EVENT);
    }

    @Test
    void should_notPropagate_when_appendFails() {
        // given
        doThrow(new IllegalStateException("mongo down")).when(appendAuditEntry).execute(EVENT);

        // when / then
        assertThatCode(() -> listener.on(EVENT)).doesNotThrowAnyException();
    }
}
