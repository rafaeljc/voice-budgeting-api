package dio.budgeting.application;

import dio.budgeting.application.input.Audio;

public interface SpeechToTextGateway {
    String transcribe(Audio audio);
}
