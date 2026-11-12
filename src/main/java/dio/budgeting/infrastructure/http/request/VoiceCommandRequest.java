package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.AudioFormat;
import dio.budgeting.application.input.IdempotencyKey;
import dio.budgeting.application.input.ProcessVoiceCommandInput;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public record VoiceCommandRequest(
        @NotNull(message = "{problem.required-part}") @AudioFile
        MultipartFile file) {

    public ProcessVoiceCommandInput toInput(UUID idempotencyKey) throws IOException {
        var audio = file.getBytes();
        // @AudioFile already guaranteed a supported format
        return new ProcessVoiceCommandInput(
                audio,
                AudioFormat.detect(audio).orElseThrow(),
                Optional.ofNullable(idempotencyKey).map(IdempotencyKey::new));
    }
}
