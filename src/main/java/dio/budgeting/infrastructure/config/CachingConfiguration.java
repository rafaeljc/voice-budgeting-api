package dio.budgeting.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Objects;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
// Lower order = outer. The Resilience4j bulkhead defaults to LOWEST_PRECEDENCE - 1, so the cache goes one step outside
// it: a hit, and a caller waiting on the sync lock of another one, never takes a permit
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 2)
@EnableConfigurationProperties(CachingProperties.class)
public class CachingConfiguration {
    private static final String VOICE_COMMAND_LOCKS = "voice-command-locks";
    private static final String RATE_LIMITERS = "rate-limiters";

    // Boot has a single spring.cache.caffeine.spec for every cache, so each cache is registered here with its own
    // limits. The caches exist at startup (static manager): a wrong name in @Cacheable fails instead of creating an
    // unbounded cache.
    @Bean
    CacheManagerCustomizer<CaffeineCacheManager> caffeineCachesCustomizer(CachingProperties properties) {
        return manager -> {
            manager.registerCustomCache("speech", builder(properties.speech()).build());
            manager.registerCustomCache(
                    VOICE_COMMAND_LOCKS, builder(properties.voiceCommandLocks()).build());
            manager.registerCustomCache(
                    RATE_LIMITERS, idleBuilder(properties.rateLimiters()).build());
        };
    }

    // Spring exposes no bean per cache: this one lets the lock adapter inject its cache instead of looking it up
    @Bean
    Cache voiceCommandLocksCache(CacheManager cacheManager) {
        return Objects.requireNonNull(
                cacheManager.getCache(VOICE_COMMAND_LOCKS), "Cache " + VOICE_COMMAND_LOCKS + " not found");
    }

    @Bean
    Cache rateLimitersCache(CacheManager cacheManager) {
        return Objects.requireNonNull(cacheManager.getCache(RATE_LIMITERS), "Cache " + RATE_LIMITERS + " not found");
    }

    private static Caffeine<Object, Object> idleBuilder(CachingProperties.IdlePolicy policy) {
        return Caffeine.newBuilder()
                .maximumSize(policy.maxEntries())
                .expireAfterAccess(policy.timeToIdle())
                .recordStats();
    }

    private static Caffeine<Object, Object> builder(CachingProperties.Policy policy) {
        return Caffeine.newBuilder()
                .maximumSize(policy.maxEntries())
                .expireAfterWrite(policy.timeToLive())
                .recordStats();
    }
}
