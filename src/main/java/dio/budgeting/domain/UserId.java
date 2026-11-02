package dio.budgeting.domain;

import java.util.Optional;
import java.util.UUID;

public record UserId(UUID uuid) {
    private static final int CANONICAL_UUID_LENGTH = 36;

    public UserId {
        if (uuid == null) {
            throw new IllegalArgumentException("UserId uuid must not be null");
        }
    }

    public static Optional<UserId> parse(String text) {
        // UUID.fromString tolerates groups shorter than the canonical form; an ID outside it is not an ID
        if (text == null || text.length() != CANONICAL_UUID_LENGTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UserId(UUID.fromString(text)));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
