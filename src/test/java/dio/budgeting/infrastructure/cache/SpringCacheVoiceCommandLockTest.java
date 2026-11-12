package dio.budgeting.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dio.budgeting.application.DuplicateVoiceCommandException;
import dio.budgeting.application.IdempotencyKeyReusedException;
import dio.budgeting.application.input.Audio;
import dio.budgeting.application.input.AudioFormat;
import dio.budgeting.application.input.IdempotencyKey;
import dio.budgeting.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

class SpringCacheVoiceCommandLockTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final IdempotencyKey KEY = new IdempotencyKey(UUID.randomUUID());
    private static final Audio AUDIO = new Audio(new byte[] {1, 2, 3}, AudioFormat.M4A);
    private static final Audio OTHER_AUDIO = new Audio(new byte[] {4, 5, 6}, AudioFormat.M4A);

    private SpringCacheVoiceCommandLock lock;

    @BeforeEach
    void setUp() {
        lock = new SpringCacheVoiceCommandLock(new ConcurrentMapCache("voice-command-locks"));
    }

    @Test
    void should_throwDuplicate_when_sameKeyAndSameAudio() {
        // given
        lock.acquire(USER_ID, KEY, AUDIO);

        // when / then
        assertThatThrownBy(() -> lock.acquire(USER_ID, KEY, AUDIO)).isInstanceOf(DuplicateVoiceCommandException.class);
    }

    @Test
    void should_throwKeyReused_when_sameKeyAndOtherAudio() {
        // given
        lock.acquire(USER_ID, KEY, AUDIO);

        // when / then
        assertThatThrownBy(() -> lock.acquire(USER_ID, KEY, OTHER_AUDIO))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void should_acceptKeyAgain_when_released() {
        // given
        lock.acquire(USER_ID, KEY, AUDIO);
        lock.release(USER_ID, KEY);

        // when / then
        assertThatCode(() -> lock.acquire(USER_ID, KEY, AUDIO)).doesNotThrowAnyException();
    }

    @Test
    void should_acceptKey_when_otherUserUsedIt() {
        // given
        lock.acquire(USER_ID, KEY, AUDIO);

        // when / then
        assertThatCode(() -> lock.acquire(new UserId(UUID.randomUUID()), KEY, AUDIO))
                .doesNotThrowAnyException();
    }
}
