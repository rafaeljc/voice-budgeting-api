package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.AudioFormat;
import dio.budgeting.application.input.ProcessVoiceCommandInput;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public record VoiceCommandRequest(
        @NotNull(message = "{problem.required-part}") @AudioFile
        MultipartFile file) {

    public ProcessVoiceCommandInput toInput() throws IOException {
        var audio = file.getBytes();
        // @AudioFile already guaranteed a supported format
        return new ProcessVoiceCommandInput(audio, AudioFormat.detect(audio).orElseThrow());
    }
}
