package dio.budgeting.application;

public class DuplicateVoiceCommandException extends RuntimeException {
    public DuplicateVoiceCommandException() {
        super("Voice command already received with this idempotency key");
    }
}
