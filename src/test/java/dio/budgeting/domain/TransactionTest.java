package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionTest {
    private static final Money AMOUNT = new Money(new BigDecimal("80.00"));
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.123Z");

    @Test
    void should_truncateCreatedAtToMillis_when_created() {
        // given
        var createdAt = Instant.parse("2026-03-01T12:30:45.123456789Z");

        // when
        var transaction = new Transaction("Mercado", AMOUNT, Category.GROCERIES, createdAt);

        // then
        assertThat(transaction.getCreatedAt()).isEqualTo(Instant.parse("2026-03-01T12:30:45.123Z"));
    }

    @ParameterizedTest
    @MethodSource("invalidCreations")
    void should_throwIllegalArgument_when_creatingInvalidTransaction(
            String description, Money amount, Category category, Instant createdAt) {
        // when / then
        assertThatThrownBy(() -> new Transaction(description, amount, category, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidCreations() {
        return Stream.of(
                Arguments.of(null, AMOUNT, Category.GROCERIES, NOW),
                Arguments.of("", AMOUNT, Category.GROCERIES, NOW),
                Arguments.of("   ", AMOUNT, Category.GROCERIES, NOW),
                Arguments.of("a".repeat(256), AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(" " + "a".repeat(256), AMOUNT, Category.GROCERIES, NOW),
                Arguments.of("Mercado", null, Category.GROCERIES, NOW),
                Arguments.of("Mercado", new Money(new BigDecimal("0.00")), Category.GROCERIES, NOW),
                Arguments.of("Mercado", new Money(new BigDecimal("-1.00")), Category.GROCERIES, NOW),
                Arguments.of("Mercado", AMOUNT, null, NOW),
                Arguments.of("Mercado", AMOUNT, Category.GROCERIES, null));
    }
}
