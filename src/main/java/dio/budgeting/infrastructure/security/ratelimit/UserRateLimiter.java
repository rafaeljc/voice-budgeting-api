package dio.budgeting.infrastructure.security.ratelimit;

import dio.budgeting.domain.UserId;

public interface UserRateLimiter {

    void acquirePermission(UserId userId, RateLimitGroup group);
}
