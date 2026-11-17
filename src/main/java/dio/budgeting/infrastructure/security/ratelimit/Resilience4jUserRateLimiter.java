package dio.budgeting.infrastructure.security.ratelimit;

import dio.budgeting.domain.UserId;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.internal.AtomicRateLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty("budgeting.rate-limit.enabled")
@EnableConfigurationProperties(RateLimitProperties.class)
public class Resilience4jUserRateLimiter implements UserRateLimiter {
    private final RateLimitProperties properties;
    private final Cache cache;

    public Resilience4jUserRateLimiter(RateLimitProperties properties, @Qualifier("rateLimitersCache") Cache cache) {
        this.properties = properties;
        this.cache = cache;
    }

    // The limiters live in the cache, not in a RateLimiterRegistry: the cache bounds the memory, and the registry would
    // publish one metric series per user
    @Override
    public void acquirePermission(UserId userId, RateLimitGroup group) {
        var limiter = cache.get(new LimiterKey(userId, group), () -> newLimiter(group));
        if (!limiter.acquirePermission()) {
            throw new RateLimitExceededException(userId, group, waitFor(limiter));
        }
    }

    private RateLimiter newLimiter(RateLimitGroup group) {
        var limit = properties.limit(group);
        // Zero timeout: refuse at once instead of holding the request, as the bulkhead does
        var config = RateLimiterConfig.custom()
                .limitForPeriod(limit.maxRequests())
                .limitRefreshPeriod(limit.period())
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiter.of(group.name(), config);
    }

    // Only the atomic implementation exposes the exact wait; RateLimiter.of always returns it
    private static Duration waitFor(RateLimiter limiter) {
        var metrics = (AtomicRateLimiter.AtomicRateLimiterMetrics) limiter.getMetrics();
        var seconds = (long) Math.ceil((double) metrics.getNanosToWait() / TimeUnit.SECONDS.toNanos(1));
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    private record LimiterKey(UserId userId, RateLimitGroup group) {}
}
