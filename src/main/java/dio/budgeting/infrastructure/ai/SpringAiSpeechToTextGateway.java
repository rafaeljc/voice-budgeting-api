package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.SpeechToTextGateway;
import dio.budgeting.application.input.Audio;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
public class SpringAiSpeechToTextGateway implements SpeechToTextGateway {
    private final TranscriptionModel transcriptionModel;

    public SpringAiSpeechToTextGateway(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @Override
    public String transcribe(Audio audio) {
        // The provider infers the audio format from the file extension, so the name carries the detected format
        var resource = new ByteArrayResource(audio.content()) {
            @Override
            public String getFilename() {
                return "audio." + audio.format().extension();
            }
        };
        return transcriptionModel.transcribe(resource);
    }
}
