package dio.budgeting.application.input;

import java.util.Objects;
import java.util.UUID;

public record IdempotencyKey(UUID uuid) {
    public IdempotencyKey {
        Objects.requireNonNull(uuid, "uuid must not be null");
    }
}
