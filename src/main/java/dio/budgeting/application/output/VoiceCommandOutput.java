package dio.budgeting.application.output;

import java.util.Optional;

public record VoiceCommandOutput(byte[] speech, Optional<TransactionProposalOutput> proposal) {}
