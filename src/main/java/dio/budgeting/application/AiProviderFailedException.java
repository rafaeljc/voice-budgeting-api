package dio.budgeting.application;

public class AiProviderFailedException extends RuntimeException {
    public AiProviderFailedException(Throwable cause) {
        super("AI provider failed", cause);
    }
}
