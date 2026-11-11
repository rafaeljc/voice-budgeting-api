package dio.budgeting.support.ai;

import java.time.Duration;

public interface AiStub {

    void givenTranscription(String text);

    void givenToolCall(String tool, String argumentsJson);

    void givenToolCalls(AiToolCall... calls);

    void givenAnswer(String text);

    void givenSpeech(byte[] audio);

    void givenDelayedSpeech(byte[] audio, Duration delay);

    String transcribedAudioFilename();

    String speechText();

    String assistantCommand();

    int assistantCallCount();

    int speechCallCount();

    void givenFailure(AiStage stage, AiFailure failure);

    void reset();
}
