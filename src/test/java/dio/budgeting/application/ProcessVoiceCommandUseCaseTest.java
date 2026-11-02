package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dio.budgeting.application.input.Audio;
import dio.budgeting.application.input.ProcessVoiceCommandInput;
import dio.budgeting.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessVoiceCommandUseCaseTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @Mock
    private SpeechToTextGateway speechToText;

    @Mock
    private AssistantGateway assistant;

    @Mock
    private TextToSpeechGateway textToSpeech;

    @InjectMocks
    private ProcessVoiceCommandUseCase useCase;

    @Test
    void should_chainStagesAndReturnSpeech_when_executed() {
        // given
        var recording = new byte[] {9, 8, 7};
        var speech = new byte[] {1, 2, 3, 4};
        var input = new ProcessVoiceCommandInput(recording, "recording.m4a", "audio/mp4");
        when(speechToText.transcribe(any(Audio.class))).thenReturn("gastei 80 reais no mercado");
        when(assistant.interpret(USER_ID, "gastei 80 reais no mercado")).thenReturn("Registrei 80 reais em mercado.");
        when(textToSpeech.synthesize("Registrei 80 reais em mercado.")).thenReturn(speech);

        // when
        var output = useCase.execute(USER_ID, input);

        // then
        var transcribed = ArgumentCaptor.forClass(Audio.class);
        verify(speechToText).transcribe(transcribed.capture());
        assertThat(transcribed.getValue().content()).isSameAs(recording);
        assertThat(transcribed.getValue().filename()).isEqualTo("recording.m4a");
        assertThat(transcribed.getValue().contentType()).isEqualTo("audio/mp4");
        assertThat(output.speech()).isSameAs(speech);
    }
}
