package dio.budgeting.infrastructure.scheduling;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dio.budgeting.application.DeleteExpiredTransactionProposalBatchUseCase;
import dio.budgeting.infrastructure.config.TransactionProposalProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionProposalExpirationJobTest {
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.000Z");
    private static final int BATCH_SIZE = 2;

    @Mock
    private DeleteExpiredTransactionProposalBatchUseCase deleteExpiredBatch;

    @Spy
    private TransactionProposalProperties properties =
            new TransactionProposalProperties(Duration.ofMinutes(5), Duration.ofMinutes(1), BATCH_SIZE, true);

    @Mock
    private Clock clock;

    @InjectMocks
    private TransactionProposalExpirationJob job;

    @Test
    void should_repeatBatchesWithSameInstantUntilIncompleteBatch_when_sweeping() {
        // given
        when(clock.instant()).thenReturn(NOW);
        when(deleteExpiredBatch.execute(NOW, BATCH_SIZE)).thenReturn(2, 2, 1);

        // when
        job.sweep();

        // then
        verify(deleteExpiredBatch, times(3)).execute(NOW, BATCH_SIZE);
        verify(clock, times(1)).instant();
    }

    @Test
    void should_stopAfterFirstBatch_when_firstBatchIsIncomplete() {
        // given
        when(clock.instant()).thenReturn(NOW);
        when(deleteExpiredBatch.execute(NOW, BATCH_SIZE)).thenReturn(1);

        // when
        job.sweep();

        // then
        verify(deleteExpiredBatch, times(1)).execute(NOW, BATCH_SIZE);
    }

    @Test
    void should_stopAfterFullBatchFollowedByEmptyOne_when_countIsMultipleOfBatchSize() {
        // given
        when(clock.instant()).thenReturn(NOW);
        when(deleteExpiredBatch.execute(NOW, BATCH_SIZE)).thenReturn(2, 0);

        // when
        job.sweep();

        // then
        verify(deleteExpiredBatch, times(2)).execute(NOW, BATCH_SIZE);
    }
}
