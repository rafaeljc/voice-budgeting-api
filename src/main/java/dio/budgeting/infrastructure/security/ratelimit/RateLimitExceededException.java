package dio.budgeting.infrastructure.security.ratelimit;

import dio.budgeting.domain.UserId;
import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {
    private final transient UserId userId;
    private final RateLimitGroup group;
    private final Duration retryAfter;

    public RateLimitExceededException(UserId userId, RateLimitGroup group, Duration retryAfter) {
        super("Rate limit exceeded for group " + group);
        this.userId = userId;
        this.group = group;
        this.retryAfter = retryAfter;
    }

    public UserId getUserId() {
        return userId;
    }

    public RateLimitGroup getGroup() {
        return group;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
