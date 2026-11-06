package dio.budgeting.domain.event;

import dio.budgeting.domain.UserId;
import java.time.Instant;

public record VoiceCommandNotUnderstoodEvent(
        UserId userId, Instant occurredAt, AudioMetadata audio, String transcription) implements DomainEvent {}
