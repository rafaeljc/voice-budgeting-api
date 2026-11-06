package dio.budgeting.domain.event;

import dio.budgeting.domain.UserAgent;
import dio.budgeting.domain.UserId;
import java.time.Instant;
import java.util.Optional;

public record VoiceCommandNotUnderstoodEvent(
        UserId userId, Optional<UserAgent> userAgent, Instant occurredAt, AudioMetadata audio, String transcription)
        implements DomainEvent {}
