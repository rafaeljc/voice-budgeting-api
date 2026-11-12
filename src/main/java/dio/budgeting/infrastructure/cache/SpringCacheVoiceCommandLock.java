package dio.budgeting.infrastructure.cache;

import dio.budgeting.application.DuplicateVoiceCommandException;
import dio.budgeting.application.IdempotencyKeyReusedException;
import dio.budgeting.application.VoiceCommandLock;
import dio.budgeting.application.input.Audio;
import dio.budgeting.application.input.IdempotencyKey;
import dio.budgeting.domain.UserId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class SpringCacheVoiceCommandLock implements VoiceCommandLock {
    static final String CACHE_NAME = "voice-command-locks";

    private final Cache cache;

    public SpringCacheVoiceCommandLock(CacheManager cacheManager) {
        this.cache = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME), "Cache " + CACHE_NAME + " not found");
    }

    // putIfAbsent is atomic and returns at once, so the loser never waits for the first request
    @Override
    public void acquire(UserId userId, IdempotencyKey key, Audio audio) {
        var fingerprint = sha256(audio.content());
        var existing = cache.putIfAbsent(new LockKey(userId.uuid(), key.uuid()), fingerprint);
        if (existing == null) {
            return;
        }
        if (fingerprint.equals(existing.get())) {
            throw new DuplicateVoiceCommandException();
        }
        throw new IdempotencyKeyReusedException();
    }

    @Override
    public void release(UserId userId, IdempotencyKey key) {
        cache.evict(new LockKey(userId.uuid(), key.uuid()));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            // Every JDK must support SHA-256
            throw new IllegalStateException(e);
        }
    }

    private record LockKey(UUID userId, UUID idempotencyKey) {}
}
