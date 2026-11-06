package dio.budgeting.infrastructure.persistence.document;

public enum AuditEntryType {
    TRANSACTION_PROPOSAL_CREATED,
    TRANSACTION_PROPOSAL_CONFIRMED,
    TRANSACTION_PROPOSAL_CANCELED,
    VOICE_COMMAND_NOT_UNDERSTOOD
}
