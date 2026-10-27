package dio.budgeting.support.ai;

public interface AiStub {

    void givenTranscription(String text);

    void givenToolCall(String tool, String argumentsJson);

    void givenAnswer(String text);

    void givenSpeech(byte[] audio);

    void reset();
}
