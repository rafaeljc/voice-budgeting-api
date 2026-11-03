package dio.budgeting.infrastructure.http.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record VoiceCommandRequest(
        @NotNull(message = "{problem.required-part}") @AudioFile
        MultipartFile file) {}
