package dio.budgeting.infrastructure.http.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dio.budgeting.application.output.VoiceCommandOutput;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceCommandResponse(SpeechResponse speech, TransactionProposalResponse transactionProposal) {
    public static VoiceCommandResponse from(VoiceCommandOutput output) {
        return new VoiceCommandResponse(
                SpeechResponse.mp3(output.speech()),
                output.proposal().map(TransactionProposalResponse::from).orElse(null));
    }
}
