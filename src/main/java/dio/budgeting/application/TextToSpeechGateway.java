package dio.budgeting.application;

public interface TextToSpeechGateway {
    byte[] synthesize(String text);
}
