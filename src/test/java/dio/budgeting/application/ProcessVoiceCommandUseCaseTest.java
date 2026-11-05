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
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionProposal;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessVoiceCommandUseCaseTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.123Z");
    private static final String COMMAND = "gastei 80 reais no mercado";
    private static final String ANSWER = "Entendi um gasto de 80 reais em mercado. Confirma?";

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
    private Clock clock;

    @InjectMocks
    private ProcessVoiceCommandUseCase useCase;

    @Test
    void should_chainStagesAndReturnSpeechWithoutProposal_when_assistantProposesNothing() {
        // given
        var recording = new byte[] {9, 8, 7};
        var speech = new byte[] {1, 2, 3, 4};
        var input = new ProcessVoiceCommandInput(recording, AudioFormat.M4A);
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput(ANSWER, Optional.empty()));
        when(textToSpeech.synthesize(ANSWER)).thenReturn(speech);

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
    }

    @Test
    void should_saveProposalAfterSpeechAndReturnIt_when_assistantProposesTransaction() {
        // given
        var speech = new byte[] {1, 2, 3, 4};
        var proposal = new ProposeTransactionInput("Mercado", new BigDecimal("80"), Category.GROCERIES);
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput(ANSWER, Optional.of(proposal)));
        when(textToSpeech.synthesize(ANSWER)).thenReturn(speech);
        when(proposalRepository.save(any(TransactionProposal.class))).then(returnsFirstArg());
        when(clock.instant()).thenReturn(NOW);

        // when
        var output = useCase.execute(USER_ID, new ProcessVoiceCommandInput(new byte[] {9}, AudioFormat.M4A));

        // then
        var saved = ArgumentCaptor.forClass(TransactionProposal.class);
        var order = inOrder(textToSpeech, proposalRepository);
        order.verify(textToSpeech).synthesize(ANSWER);
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
    }

    @Test
    void should_saveNothing_when_speechSynthesisFails() {
        // given
        var proposal = new ProposeTransactionInput("Mercado", new BigDecimal("80"), Category.GROCERIES);
        when(speechToText.transcribe(any(Audio.class))).thenReturn(COMMAND);
        when(assistant.interpret(USER_ID, COMMAND)).thenReturn(new AssistantOutput(ANSWER, Optional.of(proposal)));
        when(textToSpeech.synthesize(ANSWER)).thenThrow(new AiProviderUnavailableException(null, null));

        // when / then
        assertThatThrownBy(
                        () -> useCase.execute(USER_ID, new ProcessVoiceCommandInput(new byte[] {9}, AudioFormat.M4A)))
                .isInstanceOf(AiProviderUnavailableException.class);
        verifyNoInteractions(proposalRepository);
    }
}
