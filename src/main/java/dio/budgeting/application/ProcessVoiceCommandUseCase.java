package dio.budgeting.application;

import dio.budgeting.application.input.ProcessVoiceCommandInput;
import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.application.output.TransactionProposalOutput;
import dio.budgeting.application.output.VoiceCommandOutput;
import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.TransactionProposalTimeToLive;
import dio.budgeting.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProcessVoiceCommandUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessVoiceCommandUseCase.class);

    private final SpeechToTextGateway speechToText;
    private final AssistantGateway assistant;
    private final TextToSpeechGateway textToSpeech;
    private final TransactionProposalRepository proposalRepository;
    private final TransactionProposalTimeToLive timeToLive;
    private final Clock clock;

    public ProcessVoiceCommandUseCase(
            SpeechToTextGateway speechToText,
            AssistantGateway assistant,
            TextToSpeechGateway textToSpeech,
            TransactionProposalRepository proposalRepository,
            TransactionProposalTimeToLive timeToLive,
            Clock clock) {
        this.speechToText = speechToText;
        this.assistant = assistant;
        this.textToSpeech = textToSpeech;
        this.proposalRepository = proposalRepository;
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    public VoiceCommandOutput execute(UserId userId, ProcessVoiceCommandInput input) {
        var command = speechToText.transcribe(input.toAudio());
        log.atInfo().addKeyValue("transcriptionLength", command.length()).log("Audio transcribed");

        var interpretation = assistant.interpret(userId, command);
        log.atInfo().addKeyValue("interpretation", kindOf(interpretation)).log("Assistant answered");

        var speech = textToSpeech.synthesize(spokenAnswer(interpretation));
        log.atInfo().addKeyValue("speechSizeBytes", speech.length).log("Speech synthesized");

        // Saved last: nothing is recorded before the answer that announces it is ready, so a speech failure never
        // leaves an orphan proposal behind
        var proposal = interpretation instanceof AssistantOutput.Proposal proposed
                ? Optional.of(save(userId, proposed.proposal()))
                : Optional.<TransactionProposalOutput>empty();
        return new VoiceCommandOutput(speech, proposal);
    }

    private static String kindOf(AssistantOutput interpretation) {
        return switch (interpretation) {
            case AssistantOutput.Proposal _ -> "PROPOSAL";
            case AssistantOutput.Summary _ -> "SUMMARY";
            case AssistantOutput.NotUnderstood _ -> "NOT_UNDERSTOOD";
        };
    }

    private static String spokenAnswer(AssistantOutput interpretation) {
        return switch (interpretation) {
            case AssistantOutput.Proposal proposed -> SpokenAnswers.proposal(proposed.proposal());
            case AssistantOutput.Summary summarized -> SpokenAnswers.summary(summarized.summary());
            case AssistantOutput.NotUnderstood _ -> SpokenAnswers.notUnderstood();
        };
    }

    private TransactionProposalOutput save(UserId userId, ProposeTransactionInput proposed) {
        var proposal = proposalRepository.save(new TransactionProposal(
                userId,
                proposed.description(),
                proposed.toMoney(),
                proposed.category(),
                Instant.now(clock),
                timeToLive));
        log.atInfo()
                .addKeyValue("transactionProposalId", proposal.getId().uuid())
                .log("Transaction proposal created");
        return TransactionProposalOutput.from(proposal);
    }
}
