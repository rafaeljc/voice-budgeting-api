package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionTest {
    private static final Money AMOUNT = new Money(new BigDecimal("80.00"));
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.123Z");

    @Test
    void should_truncateCreatedAtToMillis_when_created() {
        // given
        var createdAt = Instant.parse("2026-03-01T12:30:45.123456789Z");

        // when
        var transaction = new Transaction(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, createdAt);

        // then
        assertThat(transaction.getCreatedAt()).isEqualTo(Instant.parse("2026-03-01T12:30:45.123Z"));
    }

    @Test
    void should_stripDescription_when_created() {
        // when
        var transaction = new Transaction(USER_ID, "  Mercado ", AMOUNT, Category.GROCERIES, NOW);

        // then
        assertThat(transaction.getDescription()).isEqualTo("Mercado");
    }

    @Test
    void should_acceptDescriptionWith255Characters_when_countedAfterStrip() {
        // given
        var description = "a".repeat(255);

        // when
        var transaction = new Transaction(USER_ID, " " + description + " ", AMOUNT, Category.GROCERIES, NOW);

        // then
        assertThat(transaction.getDescription()).isEqualTo(description);
    }

    @ParameterizedTest
    @MethodSource("invalidCreations")
    void should_throwIllegalArgument_when_creatingInvalidTransaction(
            UserId userId, String description, Money amount, Category category, Instant createdAt) {
        // when / then
        assertThatThrownBy(() -> new Transaction(userId, description, amount, category, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidCreations() {
        return Stream.of(
                Arguments.of(null, "Mercado", AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, null, AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "", AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "   ", AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "a".repeat(256), AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, " " + "a".repeat(256), AMOUNT, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "Mercado", null, Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "Mercado", new Money(new BigDecimal("0.00")), Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "Mercado", new Money(new BigDecimal("-1.00")), Category.GROCERIES, NOW),
                Arguments.of(USER_ID, "Mercado", AMOUNT, null, NOW),
                Arguments.of(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, null));
    }
}
