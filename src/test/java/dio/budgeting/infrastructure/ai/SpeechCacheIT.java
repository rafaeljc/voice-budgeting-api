package dio.budgeting.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dio.budgeting.application.AiProviderUnavailableException;
import dio.budgeting.application.TextToSpeechGateway;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiFailure;
import dio.budgeting.support.ai.AiStage;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    @Test
    void should_synthesizeEachText_when_textsDiffer() {
        // given
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        textToSpeechGateway.synthesize("Registrar mercado de 80 reais?");
        textToSpeechGateway.synthesize("Registrar farmácia de 30 reais?");

        // then
        assertThat(aiStub.speechCallCount()).isEqualTo(2);
    }

    @Test
    void should_synthesizeAgain_when_previousSynthesisFailed() {
        // given
        var audio = new byte[] {1, 2, 3, 4};
        aiStub.givenFailure(AiStage.SPEECH, AiFailure.PROVIDER_ERROR);
        assertThatThrownBy(() -> textToSpeechGateway.synthesize("Registrar mercado de 80 reais?"))
                .isInstanceOf(AiProviderUnavailableException.class);
        aiStub.givenSpeech(audio);

        // when
        var result = textToSpeechGateway.synthesize("Registrar mercado de 80 reais?");

        // then
        assertThat(result).isEqualTo(audio);
        assertThat(aiStub.speechCallCount()).isEqualTo(2);
    }

    @Test
    void should_synthesizeOnce_when_sameTextIsSynthesizedConcurrently() throws Exception {
        // given
        var audio = new byte[] {1, 2, 3, 4};
        var callers = 8;
        aiStub.givenDelayedSpeech(audio, Duration.ofMillis(500));
        var start = new CountDownLatch(1);

        // when
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<byte[]>>();
            for (var i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return textToSpeechGateway.synthesize("Registrar mercado de 80 reais?");
                }));
            }
            start.countDown();

            // then
            for (var future : futures) {
                assertThat(future.get()).isEqualTo(audio);
            }
        }
        assertThat(aiStub.speechCallCount()).isEqualTo(1);
    }
}
