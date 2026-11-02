package dio.budgeting.application.input;

public record ProcessVoiceCommandInput(byte[] audio, String filename, String contentType) {
    public Audio toAudio() {
        return new Audio(audio, filename, contentType);
    }
}
