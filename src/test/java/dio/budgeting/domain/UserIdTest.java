package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserIdTest {

    @Test
    void should_returnId_when_textIsValidUuid() {
        // given
        var uuid = UUID.randomUUID();

        // when
        var id = UserId.parse(uuid.toString());

        // then
        assertThat(id).contains(new UserId(uuid));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "abc", "123", "not-a-uuid-at-all", "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4"})
    void should_returnEmpty_when_textIsNotAValidUuid(String text) {
        // when
        var id = UserId.parse(text);

        // then
        assertThat(id).isEmpty();
    }

    @Test
    void should_reject_when_uuidIsNull() {
        // when / then
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
