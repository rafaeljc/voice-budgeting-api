package dio.budgeting.application.input;

import java.util.Locale;
import java.util.Optional;

public enum AudioFormat {
    M4A,
    MP3,
    WAV;

    private static final int FTYP_OFFSET = 4;
    private static final int WAVE_OFFSET = 8;
    private static final int MP3_FRAME_SYNC_MASK = 0xE0;
    private static final int MP3_LAYER_MASK = 0x06;

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    // Only the bytes decide: the name and Content-Type sent by the client are not trusted
    public static Optional<AudioFormat> detect(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (hasAt(bytes, FTYP_OFFSET, "ftyp")) {
            return Optional.of(M4A);
        }
        if (hasAt(bytes, 0, "RIFF") && hasAt(bytes, WAVE_OFFSET, "WAVE")) {
            return Optional.of(WAV);
        }
        if (hasAt(bytes, 0, "ID3") || hasMp3FrameSync(bytes)) {
            return Optional.of(MP3);
        }
        return Optional.empty();
    }

    // Layer 00 is reserved in MPEG audio and is what sets AAC ADTS (FF F1, FF F9) apart from MP3
    private static boolean hasMp3FrameSync(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & MP3_FRAME_SYNC_MASK) == MP3_FRAME_SYNC_MASK
                && (bytes[1] & MP3_LAYER_MASK) != 0;
    }

    private static boolean hasAt(byte[] bytes, int offset, String ascii) {
        if (bytes.length < offset + ascii.length()) {
            return false;
        }
        for (var i = 0; i < ascii.length(); i++) {
            if (bytes[offset + i] != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
