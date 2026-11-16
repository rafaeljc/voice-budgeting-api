package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.TextToSpeechGateway;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class SpringAiTextToSpeechGateway implements TextToSpeechGateway {
    private final TextToSpeechModel textToSpeechModel;

    public SpringAiTextToSpeechGateway(TextToSpeechModel textToSpeechModel) {
        this.textToSpeechModel = textToSpeechModel;
    }

    @Override
    @Cacheable(cacheNames = "speech", sync = true)
    @Bulkhead(name = "text-to-speech")
    public byte[] synthesize(String text) {
        return textToSpeechModel.call(text);
    }
}
