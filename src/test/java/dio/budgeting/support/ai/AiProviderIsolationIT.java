package dio.budgeting.support.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dio.budgeting.support.IntegrationTest;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;

@IntegrationTest
class AiProviderIsolationIT {

    private final TranscriptionModel transcriptionModel;

    AiProviderIsolationIT(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @Test
    void should_failWithoutExternalCall_when_aiIsNotStubbed() {
        // given
        var audio = new ByteArrayResource(new byte[] {1}) {
            @Override
            public String getFilename() {
                return "audio.m4a";
            }
        };

        // when / then
        assertThatThrownBy(() -> transcriptionModel.transcribe(audio)).hasRootCauseInstanceOf(ConnectException.class);
    }
}
