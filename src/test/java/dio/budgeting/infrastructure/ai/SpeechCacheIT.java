package dio.budgeting.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.application.TextToSpeechGateway;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import org.junit.jupiter.api.Test;

@IntegrationTest
@AutoConfigureAiStub
class SpeechCacheIT {
    private final TextToSpeechGateway textToSpeechGateway;
    private final AiStub aiStub;

    SpeechCacheIT(TextToSpeechGateway textToSpeechGateway, AiStub aiStub) {
        this.textToSpeechGateway = textToSpeechGateway;
        this.aiStub = aiStub;
    }

    @Test
    void should_synthesizeOnce_when_sameTextIsSynthesizedTwice() {
        // given
        var audio = new byte[] {1, 2, 3, 4};
        aiStub.givenSpeech(audio);

        // when
        var first = textToSpeechGateway.synthesize("Registrar mercado de 80 reais?");
        var second = textToSpeechGateway.synthesize("Registrar mercado de 80 reais?");

        // then
        assertThat(aiStub.speechCallCount()).isEqualTo(1);
        assertThat(first).isEqualTo(audio);
        assertThat(second).isEqualTo(audio);
    }
}
