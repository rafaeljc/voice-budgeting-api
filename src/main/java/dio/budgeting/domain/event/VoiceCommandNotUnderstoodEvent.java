package dio.budgeting.domain.event;

import dio.budgeting.domain.UserId;
import java.time.Instant;

public record VoiceCommandNotUnderstoodEvent(
        DomainEventId id, UserId userId, Instant occurredAt, AudioMetadata audio, String transcription)
        implements DomainEvent {}
