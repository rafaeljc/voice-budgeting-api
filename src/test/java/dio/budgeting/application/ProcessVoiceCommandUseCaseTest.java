package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dio.budgeting.application.input.Audio;
import dio.budgeting.application.input.AudioFormat;
import dio.budgeting.application.input.ProcessVoiceCommandInput;
import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.application.output.TransactionSummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.TransactionProposalTimeToLive;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.event.AudioMetadata;
import dio.budgeting.domain.event.DomainEventId;
import dio.budgeting.domain.event.TransactionProposalCreatedEvent;
import dio.budgeting.domain.event.VoiceCommandNotUnderstoodEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProcessVoiceCommandUseCaseTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.123Z");
    private static final String COMMAND = "gastei 80 reais no mercado";
    private static final String PROPOSAL_ANSWER =
            "Um gasto de R$\u00a080,00 com Mercado, na categoria mercado. Você confirma?";
    private static final String SUMMARY_ANSWER = "Você tem 3 gastos na categoria mercado, somando R$\u00a0240,00.";
    private static final String NOT_UNDERSTOOD_ANSWER = "Não entendi, tente novamente.";

    @Mock
    private SpeechToTextGateway speechToText;

    @Mock
    private AssistantGateway assistant;

    @Mock
    private TextToSpeechGateway textToSpeech;

    @Mock
    private TransactionProposalRepository proposalRepository;

    @Spy
    private TransactionProposalTimeToLive timeToLive = new TransactionProposalTimeToLive(Duration.ofMinutes(5));

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private ProcessVoiceCommandUseCase useCase;

    @Test
    void should_speakRefusalAndSaveNothing_when_assistantDoesNotUnderstand() {
        // given
        var recording = new byte[] {9, 8, 7};
        var speech = new byte[] {1, 2, 3, 4};
        var input = new ProcessVoiceCommandInput(recording, AudioFormat.M4A);
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput.NotUnderstood());
        when(textToSpeech.synthesize(NOT_UNDERSTOOD_ANSWER)).thenReturn(speech);
        when(clock.instant()).thenReturn(NOW);

        // when
        var output = useCase.execute(USER_ID, input);

        // then
        var transcribed = ArgumentCaptor.forClass(Audio.class);
        verify(speechToText).transcribe(transcribed.capture());
        assertThat(transcribed.getValue().content()).isSameAs(recording);
        assertThat(transcribed.getValue().format()).isEqualTo(AudioFormat.M4A);
        assertThat(output.speech()).isSameAs(speech);
        assertThat(output.proposal()).isEmpty();
        verifyNoInteractions(proposalRepository);
        var published = ArgumentCaptor.forClass(VoiceCommandNotUnderstoodEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().id()).isNotNull();
        assertThat(published.getValue())
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(new VoiceCommandNotUnderstoodEvent(
                        new DomainEventId(), USER_ID, NOW, new AudioMetadata("M4A", 3), COMMAND));
    }

    @Test
    void should_saveProposalAfterSpeechAndReturnIt_when_assistantProposesTransaction() {
        // given
        var speech = new byte[] {1, 2, 3, 4};
        var proposal = new ProposeTransactionInput("Mercado", new BigDecimal("80"), Category.GROCERIES);
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput.Proposal(proposal));
        when(textToSpeech.synthesize(PROPOSAL_ANSWER)).thenReturn(speech);
        when(proposalRepository.save(any(TransactionProposal.class))).then(returnsFirstArg());
        when(clock.instant()).thenReturn(NOW);

        // when
        var output = useCase.execute(USER_ID, new ProcessVoiceCommandInput(new byte[] {9}, AudioFormat.M4A));

        // then
        var saved = ArgumentCaptor.forClass(TransactionProposal.class);
        var order = inOrder(textToSpeech, proposalRepository);
        order.verify(textToSpeech).synthesize(PROPOSAL_ANSWER);
        order.verify(proposalRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getDescription()).isEqualTo("Mercado");
        assertThat(saved.getValue().getAmount()).isEqualTo(new Money(new BigDecimal("80.00")));
        assertThat(saved.getValue().getCategory()).isEqualTo(Category.GROCERIES);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(output.speech()).isSameAs(speech);
        assertThat(output.proposal()).hasValueSatisfying(returned -> {
            assertThat(returned.id()).isEqualTo(saved.getValue().getId().uuid().toString());
            assertThat(returned.description()).isEqualTo("Mercado");
            assertThat(returned.amount()).isEqualByComparingTo("80.00");
            assertThat(returned.currency()).isEqualTo("BRL");
            assertThat(returned.category()).isEqualTo("GROCERIES");
            assertThat(returned.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        });
        var published = ArgumentCaptor.forClass(TransactionProposalCreatedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().id()).isNotNull();
        assertThat(published.getValue())
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(new TransactionProposalCreatedEvent(
                        new DomainEventId(),
                        USER_ID,
                        NOW,
                        new AudioMetadata("M4A", 1),
                        COMMAND,
                        saved.getValue().getId(),
                        "Mercado",
                        new Money(new BigDecimal("80.00")),
                        Category.GROCERIES,
                        NOW.plus(Duration.ofMinutes(5))));
    }

    @Test
    void should_speakSummaryAndSaveNothing_when_assistantSummarizesCategory() {
        // given
        var speech = new byte[] {1, 2, 3, 4};
        var summary = new TransactionSummaryOutput(Category.GROCERIES, 3, new Money(new BigDecimal("240.00")));
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput.Summary(summary));
        when(textToSpeech.synthesize(SUMMARY_ANSWER)).thenReturn(speech);

        // when
        var output = useCase.execute(USER_ID, new ProcessVoiceCommandInput(new byte[] {9}, AudioFormat.M4A));

        // then
        assertThat(output.speech()).isSameAs(speech);
        assertThat(output.proposal()).isEmpty();
        verifyNoInteractions(proposalRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void should_saveNothing_when_speechSynthesisFails() {
        // given
        var proposal = new ProposeTransactionInput("Mercado", new BigDecimal("80"), Category.GROCERIES);
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput.Proposal(proposal));
        when(textToSpeech.synthesize(PROPOSAL_ANSWER)).thenThrow(new AiProviderUnavailableException(null, null));

        // when / then
        assertThatThrownBy(
                        () -> useCase.execute(USER_ID, new ProcessVoiceCommandInput(new byte[] {9}, AudioFormat.M4A)))
                .isInstanceOf(AiProviderUnavailableException.class);
        verifyNoInteractions(proposalRepository);
        verifyNoInteractions(eventPublisher);
    }
}
