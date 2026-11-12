package dio.budgeting.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CachingProperties.class)
public class CachingConfiguration {

    // Boot has a single spring.cache.caffeine.spec for every cache, so each cache is registered here with its own
    // limits. The caches exist at startup (static manager): a wrong name in @Cacheable fails instead of creating an
    // unbounded cache.
    @Bean
    CacheManagerCustomizer<CaffeineCacheManager> caffeineCachesCustomizer(CachingProperties properties) {
        return manager -> {
            manager.registerCustomCache("speech", build(properties.speech()));
            manager.registerCustomCache("voice-command-locks", build(properties.voiceCommandLocks()));
        };
    }

    private static Cache<Object, Object> build(CachingProperties.Policy policy) {
        return Caffeine.newBuilder()
                .maximumSize(policy.maxEntries())
                .expireAfterWrite(policy.timeToLive())
                .recordStats()
                .build();
    }
}
