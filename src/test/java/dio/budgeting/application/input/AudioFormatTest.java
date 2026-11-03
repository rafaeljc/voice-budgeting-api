package dio.budgeting.application.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AudioFormatTest {

    @ParameterizedTest
    @MethodSource("recognizedSignatures")
    void should_detectFormat_when_bytesHaveItsSignature(byte[] bytes, AudioFormat expected) {
        // when
        var format = AudioFormat.detect(bytes);

        // then
        assertThat(format).contains(expected);
    }

    @ParameterizedTest
    @MethodSource("unrecognizedBytes")
    void should_returnEmpty_when_bytesAreNotSupportedAudio(byte[] bytes) {
        // when
        var format = AudioFormat.detect(bytes);

        // then
        assertThat(format).isEmpty();
    }

    @Test
    void should_returnEmpty_when_bytesAreNull() {
        // when
        var format = AudioFormat.detect(null);

        // then
        assertThat(format).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AudioFormat.class)
    void should_exposeLowercaseExtension_when_formatIsAsked(AudioFormat format) {
        // when
        var extension = format.extension();

        // then
        assertThat(extension).isEqualTo(format.name().toLowerCase(Locale.ROOT));
    }

    static Stream<Arguments> recognizedSignatures() {
        return Stream.of(
                Arguments.of(bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32), AudioFormat.M4A),
                Arguments.of(bytes(0, 0, 0, 0x20, 0x66, 0x74, 0x79, 0x70, 0x4D, 0x34, 0x41, 0x20), AudioFormat.M4A),
                Arguments.of(bytes(0x49, 0x44, 0x33, 4, 0), AudioFormat.MP3),
                Arguments.of(bytes(0xFF, 0xFB, 0x90, 0x00), AudioFormat.MP3),
                Arguments.of(bytes(0xFF, 0xE3, 0x18, 0x00), AudioFormat.MP3),
                Arguments.of(bytes(0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4, 0x57, 0x41, 0x56, 0x45), AudioFormat.WAV));
    }

    static Stream<byte[]> unrecognizedBytes() {
        return Stream.of(
                new byte[0],
                bytes(0xFF),
                // ADTS AAC has the MP3 frame sync but layer 00
                bytes(0xFF, 0xF1, 0x50, 0x80),
                bytes(0xFF, 0xF9, 0x50, 0x80),
                // sync without the three bits that complete it
                bytes(0xFF, 0xC3, 0x90, 0x00),
                bytes(0x25, 0x50, 0x44, 0x46, 0x2D),
                "just some text".getBytes(StandardCharsets.UTF_8),
                // too short for the full signatures
                bytes(0x49, 0x44),
                bytes(0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4),
                bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79),
                // RIFF that is not WAVE
                bytes(0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4, 0x41, 0x56, 0x49, 0x20));
    }

    private static byte[] bytes(int... values) {
        var result = new byte[values.length];
        for (var i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
