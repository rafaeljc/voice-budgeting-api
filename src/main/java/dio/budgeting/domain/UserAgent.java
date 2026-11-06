package dio.budgeting.domain;

import java.util.Optional;

public record UserAgent(String value) {
    private static final int MAX_LENGTH = 256;

    public UserAgent {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserAgent value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("UserAgent value must have at most " + MAX_LENGTH + " characters");
        }
    }

    public static Optional<UserAgent> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        var stripped = header.strip();
        return Optional.of(new UserAgent(stripped.substring(0, Math.min(stripped.length(), MAX_LENGTH))));
    }
}
