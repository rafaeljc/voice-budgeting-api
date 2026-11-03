package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.AudioFormat;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.multipart.MultipartFile;

public class AudioFileValidator implements ConstraintValidator<AudioFile, MultipartFile> {
    private static final String EMPTY_MESSAGE = "{voice-command.file.empty}";
    private static final String UNSUPPORTED_FORMAT_MESSAGE = "{voice-command.file.unsupported-format}";

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        // A missing part is reported by @NotNull
        if (file == null) {
            return true;
        }
        if (file.isEmpty()) {
            return reject(context, EMPTY_MESSAGE);
        }
        if (AudioFormat.detect(bytesOf(file)).isEmpty()) {
            return reject(context, UNSUPPORTED_FORMAT_MESSAGE);
        }
        return true;
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
