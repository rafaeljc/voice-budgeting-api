package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionIdTest {

    @Test
    void should_returnId_when_textIsValidUuid() {
        // given
        var uuid = UUID.randomUUID();

        // when
        var id = TransactionId.parse(uuid.toString());

        // then
        assertThat(id).contains(new TransactionId(uuid));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "abc", "123", "not-a-uuid-at-all", "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4"})
    void should_returnEmpty_when_textIsNotAValidUuid(String text) {
        // when
        var id = TransactionId.parse(text);

        // then
        assertThat(id).isEmpty();
    }
}
