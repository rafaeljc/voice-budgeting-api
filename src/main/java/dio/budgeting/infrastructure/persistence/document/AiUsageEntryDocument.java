package dio.budgeting.infrastructure.persistence.document;

import dio.budgeting.infrastructure.ai.event.AiUsageMeasuredEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One flat document per model response, with optional fields per stage: a null field is not written
@Document("ai_usage_entries")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiUsageEntryDocument {
    @Id
    private UUID id;

    private UUID userId;
    private String traceId;
    private Instant occurredAt;
    private AiStage stage;
    private String model;
    private Double transcribedAudioSeconds;
    private Long inputTokens;
    private Long cachedInputTokens;
    private Long outputTokens;
    private Integer synthesizedCharacters;

    public static AiUsageEntryDocument from(AiUsageMeasuredEvent event) {
        var entry = new AiUsageEntryDocument();
        entry.setId(UUID.randomUUID());
        entry.setUserId(event.userId().uuid());
        entry.setTraceId(event.traceId());
        entry.setOccurredAt(event.occurredAt());
        entry.setModel(event.model());
        switch (event.usage()) {
            case AiUsageMeasuredEvent.Usage.Transcription transcription -> {
                entry.setStage(AiStage.TRANSCRIPTION);
                entry.setTranscribedAudioSeconds(transcription.audioSeconds());
            }
            case AiUsageMeasuredEvent.Usage.Assistant assistant -> {
                entry.setStage(AiStage.ASSISTANT);
                entry.setInputTokens(assistant.inputTokens());
                entry.setCachedInputTokens(assistant.cachedInputTokens());
                entry.setOutputTokens(assistant.outputTokens());
            }
            case AiUsageMeasuredEvent.Usage.Speech speech -> {
                entry.setStage(AiStage.SPEECH);
                entry.setSynthesizedCharacters(speech.characters());
            }
        }
        return entry;
    }
}
