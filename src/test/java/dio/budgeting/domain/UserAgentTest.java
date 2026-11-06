package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAgentTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void should_returnEmpty_when_headerIsNullOrBlank(String header) {
        assertThat(UserAgent.parse(header)).isEmpty();
    }

    @Test
    void should_stripSurroundingSpaces_when_parsing() {
        assertThat(UserAgent.parse("  okhttp/4.12  ")).contains(new UserAgent("okhttp/4.12"));
    }

    @Test
    void should_truncateTo256Characters_when_headerIsLonger() {
        var parsed = UserAgent.parse("a".repeat(300));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().value()).hasSize(256);
    }

    @Test
    void should_acceptValueWith256Characters() {
        assertThat(new UserAgent("a".repeat(256)).value()).hasSize(256);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void should_throw_when_valueIsNullOrBlank(String value) {
        assertThatThrownBy(() -> new UserAgent(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_valueHasMoreThan256Characters() {
        assertThatThrownBy(() -> new UserAgent("a".repeat(257))).isInstanceOf(IllegalArgumentException.class);
    }
}
