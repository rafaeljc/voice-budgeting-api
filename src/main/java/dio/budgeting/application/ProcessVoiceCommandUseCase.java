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
import dio.budgeting.domain.event.AudioMetadata;
import dio.budgeting.domain.event.DomainEventId;
import dio.budgeting.domain.event.TransactionProposalCreatedEvent;
import dio.budgeting.domain.event.VoiceCommandNotUnderstoodEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ProcessVoiceCommandUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessVoiceCommandUseCase.class);

    private final SpeechToTextGateway speechToText;
    private final AssistantGateway assistant;
    private final TextToSpeechGateway textToSpeech;
    private final TransactionProposalRepository proposalRepository;
    private final TransactionProposalTimeToLive timeToLive;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ProcessVoiceCommandUseCase(
            SpeechToTextGateway speechToText,
            AssistantGateway assistant,
            TextToSpeechGateway textToSpeech,
            TransactionProposalRepository proposalRepository,
            TransactionProposalTimeToLive timeToLive,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.speechToText = speechToText;
        this.assistant = assistant;
        this.textToSpeech = textToSpeech;
        this.proposalRepository = proposalRepository;
        this.timeToLive = timeToLive;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public VoiceCommandOutput execute(UserId userId, ProcessVoiceCommandInput input) {
        var command = speechToText.transcribe(input.toAudio());
        log.atInfo().addKeyValue("transcriptionLength", command.length()).log("Audio transcribed");

        var interpretation = assistant.interpret(userId, command);
        log.atInfo().addKeyValue("interpretation", kindOf(interpretation)).log("Assistant answered");

        var speech = textToSpeech.synthesize(spokenAnswer(interpretation));
        log.atInfo().addKeyValue("speechSizeBytes", speech.length).log("Speech synthesized");

        // Saved and published last: nothing is recorded before the answer that announces it is ready, so a speech
        // failure never leaves an orphan proposal or audit entry behind
        var audio = new AudioMetadata(input.format().name(), input.audio().length);
        return switch (interpretation) {
            case AssistantOutput.Proposal proposed -> {
                var proposal = save(userId, proposed.proposal());
                eventPublisher.publishEvent(new TransactionProposalCreatedEvent(
                        new DomainEventId(),
                        userId,
                        proposal.getCreatedAt(),
                        audio,
                        command,
                        proposal.getId(),
                        proposal.getDescription(),
                        proposal.getAmount(),
                        proposal.getCategory(),
                        proposal.getExpiresAt()));
                yield new VoiceCommandOutput(speech, Optional.of(TransactionProposalOutput.from(proposal)));
            }
            case AssistantOutput.Summary _ -> new VoiceCommandOutput(speech, Optional.empty());
            case AssistantOutput.NotUnderstood _ -> {
                eventPublisher.publishEvent(new VoiceCommandNotUnderstoodEvent(
                        new DomainEventId(), userId, Instant.now(clock), audio, command));
                yield new VoiceCommandOutput(speech, Optional.empty());
            }
        };
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

    private TransactionProposal save(UserId userId, ProposeTransactionInput proposed) {
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
        return proposal;
    }
}
