package dio.budgeting.application;

import dio.budgeting.application.input.ProcessVoiceCommandInput;
import dio.budgeting.application.output.VoiceCommandOutput;
import dio.budgeting.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessVoiceCommandUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessVoiceCommandUseCase.class);

    private final SpeechToTextGateway speechToText;
    private final AssistantGateway assistant;
    private final TextToSpeechGateway textToSpeech;

    public ProcessVoiceCommandUseCase(
            SpeechToTextGateway speechToText, AssistantGateway assistant, TextToSpeechGateway textToSpeech) {
        this.speechToText = speechToText;
        this.assistant = assistant;
        this.textToSpeech = textToSpeech;
    }

    public VoiceCommandOutput execute(UserId userId, ProcessVoiceCommandInput input) {
        var command = speechToText.transcribe(input.toAudio());
        log.atInfo().addKeyValue("transcriptionLength", command.length()).log("Audio transcribed");

        var answer = assistant.interpret(userId, command);
        log.atInfo().addKeyValue("answerLength", answer.length()).log("Assistant answered");

        var speech = textToSpeech.synthesize(answer);
        log.atInfo().addKeyValue("speechSizeBytes", speech.length).log("Speech synthesized");

        return new VoiceCommandOutput(speech);
    }
}
