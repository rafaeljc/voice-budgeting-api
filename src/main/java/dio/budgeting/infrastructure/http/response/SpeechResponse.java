package dio.budgeting.infrastructure.http.response;

public record SpeechResponse(String contentType, byte[] content) {
    private static final String MP3 = "audio/mpeg";

    public static SpeechResponse mp3(byte[] content) {
        return new SpeechResponse(MP3, content);
    }
}
