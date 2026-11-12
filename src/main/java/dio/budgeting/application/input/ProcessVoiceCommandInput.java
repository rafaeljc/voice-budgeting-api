package dio.budgeting.application.input;

import java.util.Optional;

public record ProcessVoiceCommandInput(byte[] audio, AudioFormat format, Optional<IdempotencyKey> idempotencyKey) {
    public ProcessVoiceCommandInput(byte[] audio, AudioFormat format) {
        this(audio, format, Optional.empty());
    }

    public Audio toAudio() {
        return new Audio(audio, format);
    }
}
