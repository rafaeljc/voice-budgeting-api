package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void should_keepValue_when_scaleIsTwo() {
        // when
        var money = new Money(new BigDecimal("80.00"));

        // then
        assertThat(money.value()).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void should_rejectNull_when_created() {
        // when / then
        assertThatThrownBy(() -> new Money(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", "80.5", "80.000", "80.005"})
    void should_rejectValue_when_scaleIsNotTwo(String value) {
        // when / then
        assertThatThrownBy(() -> new Money(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_acceptSixteenIntegerDigits_when_created() {
        // when
        var money = new Money(new BigDecimal("9999999999999999.99"));

        // then
        assertThat(money.value()).isEqualTo(new BigDecimal("9999999999999999.99"));
    }

    @Test
    void should_rejectValue_when_moreThanSixteenIntegerDigits() {
        // when / then
        assertThatThrownBy(() -> new Money(new BigDecimal("10000000000000000.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", "80.0", "80.00", "80.000"})
    void should_normalizeToScaleTwo_when_createdWithOf(String value) {
        // when
        var money = Money.of(new BigDecimal(value));

        // then
        assertThat(money).isEqualTo(new Money(new BigDecimal("80.00")));
    }

    @Test
    void should_rejectValue_when_ofWouldRound() {
        // when / then
        assertThatThrownBy(() -> Money.of(new BigDecimal("80.005"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_rejectNull_when_createdWithOf() {
        // when / then
        assertThatThrownBy(() -> Money.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
