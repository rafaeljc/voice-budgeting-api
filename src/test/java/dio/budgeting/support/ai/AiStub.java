package dio.budgeting.support.ai;

public interface AiStub {

    void givenTranscription(String text);

    void givenToolCall(String tool, String argumentsJson);

    void givenToolCalls(AiToolCall... calls);

    void givenAnswer(String text);

    void givenSpeech(byte[] audio);

    String transcribedAudioFilename();

    String speechText();

    String assistantCommand();

    int assistantCallCount();

    void givenFailure(AiStage stage, AiFailure failure);

    void reset();
}
