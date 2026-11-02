package dio.budgeting.application;

public class AiProviderUnavailableException extends RuntimeException {
    private final Integer providerStatus;

    // providerStatus is null when the failure happened before any provider response (network)
    public AiProviderUnavailableException(Integer providerStatus, Throwable cause) {
        super("AI provider unavailable", cause);
        this.providerStatus = providerStatus;
    }

    public Integer getProviderStatus() {
        return providerStatus;
    }
}
