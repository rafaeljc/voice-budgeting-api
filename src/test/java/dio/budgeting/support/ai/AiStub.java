package dio.budgeting.support.ai;

public interface AiStub {

    void givenTranscription(String text);

    void givenToolCall(String tool, String argumentsJson);

    void givenToolCalls(String tool, String... argumentsJson);

    void givenAnswer(String text);

    void givenSpeech(byte[] audio);

    String transcribedAudioFilename();

    void givenFailure(AiStage stage, AiFailure failure);

    void reset();
}
