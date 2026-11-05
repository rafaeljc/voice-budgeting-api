package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalNotFoundException;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.TransactionProposalTimeToLive;
import dio.budgeting.domain.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelTransactionProposalUseCaseTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final Instant PROPOSED_AT = Instant.parse("2026-03-01T12:30:45.123Z");
    private static final Instant NOW = Instant.parse("2026-03-01T12:35:00.000Z");
    private static final TransactionProposalTimeToLive TIME_TO_LIVE =
            new TransactionProposalTimeToLive(Duration.ofMinutes(5));

    @Mock
    private TransactionProposalRepository repository;

    @Mock
    private Clock clock;

    @InjectMocks
    private CancelTransactionProposalUseCase useCase;

    @Test
    void should_deleteProposal_when_proposalExists() {
        // given
        var proposal = new TransactionProposal(
                USER_ID, "Mercado", new Money(new BigDecimal("80.00")), Category.GROCERIES, PROPOSED_AT, TIME_TO_LIVE);
        when(clock.instant()).thenReturn(NOW);
        when(repository.findByIdAndUserIdForUpdate(proposal.getId(), USER_ID)).thenReturn(Optional.of(proposal));

        // when
        useCase.execute(USER_ID, proposal.getId());

        // then
        verify(repository).delete(proposal);
    }

    @Test
    void should_throwNotFound_when_proposalDoesNotExist() {
        // given
        var id = new TransactionProposalId();
        when(clock.instant()).thenReturn(NOW);
        when(repository.findByIdAndUserIdForUpdate(id, USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> useCase.execute(USER_ID, id)).isInstanceOf(TransactionProposalNotFoundException.class);
    }

    @Test
    void should_throwNotFoundAndDeleteNothing_when_proposalIsExpired() {
        // given
        var proposal = new TransactionProposal(
                USER_ID, "Mercado", new Money(new BigDecimal("80.00")), Category.GROCERIES, PROPOSED_AT, TIME_TO_LIVE);
        when(repository.findByIdAndUserIdForUpdate(proposal.getId(), USER_ID)).thenReturn(Optional.of(proposal));
        when(clock.instant()).thenReturn(proposal.getExpiresAt());

        // when / then
        assertThatThrownBy(() -> useCase.execute(USER_ID, proposal.getId()))
                .isInstanceOf(TransactionProposalNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
