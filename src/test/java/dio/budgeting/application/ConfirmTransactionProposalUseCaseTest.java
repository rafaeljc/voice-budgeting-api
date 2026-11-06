package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalNotFoundException;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.TransactionProposalTimeToLive;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.event.DomainEventId;
import dio.budgeting.domain.event.TransactionProposalConfirmedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ConfirmTransactionProposalUseCaseTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final Instant PROPOSED_AT = Instant.parse("2026-03-01T12:30:45.123Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-03-01T12:35:00.000Z");
    private static final TransactionProposalTimeToLive TIME_TO_LIVE =
            new TransactionProposalTimeToLive(Duration.ofMinutes(5));

    @Mock
    private TransactionProposalRepository proposalRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private ConfirmTransactionProposalUseCase useCase;

    @Test
    void should_saveTransactionAtConfirmationInstantAndDeleteProposal_when_proposalExists() {
        // given
        var proposal = new TransactionProposal(
                USER_ID, "Mercado", new Money(new BigDecimal("80.00")), Category.GROCERIES, PROPOSED_AT, TIME_TO_LIVE);
        when(proposalRepository.findByIdAndUserIdForUpdate(proposal.getId(), USER_ID))
                .thenReturn(Optional.of(proposal));
        when(transactionRepository.save(any(Transaction.class))).then(returnsFirstArg());
        when(clock.instant()).thenReturn(CONFIRMED_AT);

        // when
        var output = useCase.execute(USER_ID, proposal.getId());

        // then
        var saved = ArgumentCaptor.forClass(Transaction.class);
        var order = inOrder(transactionRepository, proposalRepository);
        order.verify(transactionRepository).save(saved.capture());
        order.verify(proposalRepository).delete(proposal);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getDescription()).isEqualTo("Mercado");
        assertThat(saved.getValue().getAmount()).isEqualTo(new Money(new BigDecimal("80.00")));
        assertThat(saved.getValue().getCategory()).isEqualTo(Category.GROCERIES);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(output.id()).isEqualTo(saved.getValue().getId().uuid().toString());
        assertThat(output.createdAt()).isEqualTo(CONFIRMED_AT);
        var published = ArgumentCaptor.forClass(TransactionProposalConfirmedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().id()).isNotNull();
        assertThat(published.getValue())
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(new TransactionProposalConfirmedEvent(
                        new DomainEventId(),
                        USER_ID,
                        CONFIRMED_AT,
                        proposal.getId(),
                        saved.getValue().getId()));
    }

    @Test
    void should_throwNotFoundAndSaveNothing_when_proposalDoesNotExist() {
        // given
        var id = new TransactionProposalId();
        when(clock.instant()).thenReturn(CONFIRMED_AT);
        when(proposalRepository.findByIdAndUserIdForUpdate(id, USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> useCase.execute(USER_ID, id)).isInstanceOf(TransactionProposalNotFoundException.class);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void should_throwNotFoundAndSaveNothing_when_proposalIsExpired() {
        // given
        var proposal = new TransactionProposal(
                USER_ID, "Mercado", new Money(new BigDecimal("80.00")), Category.GROCERIES, PROPOSED_AT, TIME_TO_LIVE);
        when(proposalRepository.findByIdAndUserIdForUpdate(proposal.getId(), USER_ID))
                .thenReturn(Optional.of(proposal));
        when(clock.instant()).thenReturn(proposal.getExpiresAt());

        // when / then
        assertThatThrownBy(() -> useCase.execute(USER_ID, proposal.getId()))
                .isInstanceOf(TransactionProposalNotFoundException.class);
        verifyNoInteractions(transactionRepository);
        verify(proposalRepository, never()).delete(any());
        verifyNoInteractions(eventPublisher);
    }
}
