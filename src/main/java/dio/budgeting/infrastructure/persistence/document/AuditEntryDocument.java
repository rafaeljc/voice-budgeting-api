package dio.budgeting.infrastructure.persistence.document;

import dio.budgeting.domain.Money;
import dio.budgeting.domain.event.AudioMetadata;
import dio.budgeting.domain.event.DomainEvent;
import dio.budgeting.domain.event.TransactionProposalCanceledEvent;
import dio.budgeting.domain.event.TransactionProposalConfirmedEvent;
import dio.budgeting.domain.event.TransactionProposalCreatedEvent;
import dio.budgeting.domain.event.VoiceCommandNotUnderstoodEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

// One flat document with optional fields per type: a null field is not written
@Document("audit_entries")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditEntryDocument {
    @Id
    private ObjectId id;

    private AuditEntryType type;
    private UUID userId;
    private Instant occurredAt;
    private String traceId;
    private UUID transactionProposalId;
    private UUID transactionId;
    private Audio audio;
    private String transcription;
    private Proposal proposal;

    public record Audio(String format, long sizeBytes) {
        static Audio from(AudioMetadata audio) {
            return new Audio(audio.format(), audio.sizeBytes());
        }
    }

    public record Proposal(
            String description,
            // Decimal128, not the default text: a text amount cannot be aggregated
            @Field(targetType = FieldType.DECIMAL128) BigDecimal amount,
            String currency,
            String category,
            Instant expiresAt) {
        static Proposal from(TransactionProposalCreatedEvent event) {
            return new Proposal(
                    event.description(),
                    event.amount().value(),
                    Money.CURRENCY,
                    event.category().name(),
                    event.expiresAt());
        }
    }

    public static AuditEntryDocument from(DomainEvent event, String traceId) {
        return switch (event) {
            case TransactionProposalCreatedEvent created -> {
                var entry = of(AuditEntryType.TRANSACTION_PROPOSAL_CREATED, created, traceId);
                entry.setTransactionProposalId(created.transactionProposalId().uuid());
                entry.setAudio(Audio.from(created.audio()));
                entry.setTranscription(created.transcription());
                entry.setProposal(Proposal.from(created));
                yield entry;
            }
            case VoiceCommandNotUnderstoodEvent notUnderstood -> {
                var entry = of(AuditEntryType.VOICE_COMMAND_NOT_UNDERSTOOD, notUnderstood, traceId);
                entry.setAudio(Audio.from(notUnderstood.audio()));
                entry.setTranscription(notUnderstood.transcription());
                yield entry;
            }
            case TransactionProposalConfirmedEvent confirmed -> {
                var entry = of(AuditEntryType.TRANSACTION_PROPOSAL_CONFIRMED, confirmed, traceId);
                entry.setTransactionProposalId(confirmed.transactionProposalId().uuid());
                entry.setTransactionId(confirmed.transactionId().uuid());
                yield entry;
            }
            case TransactionProposalCanceledEvent canceled -> {
                var entry = of(AuditEntryType.TRANSACTION_PROPOSAL_CANCELED, canceled, traceId);
                entry.setTransactionProposalId(canceled.transactionProposalId().uuid());
                yield entry;
            }
        };
    }

    private static AuditEntryDocument of(AuditEntryType type, DomainEvent event, String traceId) {
        var entry = new AuditEntryDocument();
        entry.setType(type);
        entry.setUserId(event.userId().uuid());
        entry.setOccurredAt(event.occurredAt());
        entry.setTraceId(traceId);
        return entry;
    }
}
