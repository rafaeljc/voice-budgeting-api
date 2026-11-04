package dio.budgeting.application;

import dio.budgeting.application.input.ProcessVoiceCommandInput;
import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.TransactionProposalOutput;
import dio.budgeting.application.output.VoiceCommandOutput;
import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalRepository;
import dio.budgeting.domain.UserId;
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    public ProcessVoiceCommandUseCase(
            SpeechToTextGateway speechToText,
            AssistantGateway assistant,
            TextToSpeechGateway textToSpeech,
            TransactionProposalRepository proposalRepository,
            Clock clock) {
        this.speechToText = speechToText;
        this.assistant = assistant;
        this.textToSpeech = textToSpeech;
        this.proposalRepository = proposalRepository;
        this.clock = clock;
    }

    public VoiceCommandOutput execute(UserId userId, ProcessVoiceCommandInput input) {
        var command = speechToText.transcribe(input.toAudio());
        log.atInfo().addKeyValue("transcriptionLength", command.length()).log("Audio transcribed");

        var answer = assistant.interpret(userId, command);
        log.atInfo().addKeyValue("answerLength", answer.text().length()).log("Assistant answered");

        var speech = textToSpeech.synthesize(answer.text());
        log.atInfo().addKeyValue("speechSizeBytes", speech.length).log("Speech synthesized");

        // Saved last: nothing is recorded before the answer that announces it is ready, so a speech failure never
        // leaves an orphan proposal behind
        var proposal = answer.proposal().map(proposed -> save(userId, proposed));
        return new VoiceCommandOutput(speech, proposal);
    }

    private TransactionProposalOutput save(UserId userId, ProposeTransactionInput proposed) {
        var proposal = proposalRepository.save(new TransactionProposal(
                userId, proposed.description(), proposed.toMoney(), proposed.category(), Instant.now(clock)));
        log.atInfo()
                .addKeyValue("transactionProposalId", proposal.getId().uuid())
                .log("Transaction proposal created");
        return TransactionProposalOutput.from(proposal);
    }
}
