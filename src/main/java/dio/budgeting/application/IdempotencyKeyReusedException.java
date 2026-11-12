package dio.budgeting.application;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super("Idempotency key already used with a different audio");
    }
}
