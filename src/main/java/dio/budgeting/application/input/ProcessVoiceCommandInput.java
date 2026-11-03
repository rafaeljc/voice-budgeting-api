package dio.budgeting.application.input;

public record ProcessVoiceCommandInput(byte[] audio, AudioFormat format) {
    public Audio toAudio() {
        return new Audio(audio, format);
    }
}
