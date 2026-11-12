package dio.budgeting.application;

import dio.budgeting.application.input.Audio;
import dio.budgeting.application.input.IdempotencyKey;
import dio.budgeting.domain.UserId;

public interface VoiceCommandLock {
    void acquire(UserId userId, IdempotencyKey key, Audio audio);

    void release(UserId userId, IdempotencyKey key);
}
