package dio.budgeting.infrastructure.ai.event;

import dio.budgeting.domain.UserId;
import java.time.Instant;

public record AiUsageMeasuredEvent(UserId userId, String traceId, Instant occurredAt, String model, Usage usage) {

    public sealed interface Usage {
        record Transcription(double audioSeconds) implements Usage {}

        record Assistant(long inputTokens, long cachedInputTokens, long outputTokens) implements Usage {}

        // Characters approximate the cost of gpt-4o-mini-tts (billed by text and audio tokens): the synthesis
        // returns no usage
        record Speech(int characters) implements Usage {}
    }
}
