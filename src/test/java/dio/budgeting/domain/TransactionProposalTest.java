package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionProposalTest {
    private static final Money AMOUNT = new Money(new BigDecimal("80.00"));
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:45.123Z");
    private static final TransactionProposalTimeToLive TIME_TO_LIVE =
            new TransactionProposalTimeToLive(Duration.ofMinutes(5));

    @Test
    void should_truncateCreatedAtToMillis_when_created() {
        // given
        var createdAt = Instant.parse("2026-03-01T12:30:45.123456789Z");

        // when
        var proposal = new TransactionProposal(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, createdAt, TIME_TO_LIVE);

        // then
        assertThat(proposal.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_stripDescription_when_created() {
        // when
        var proposal = new TransactionProposal(USER_ID, "  Mercado ", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE);

        // then
        assertThat(proposal.getDescription()).isEqualTo("Mercado");
    }

    @Test
    void should_acceptDescriptionWith255Characters_when_countedAfterStrip() {
        // given
        var description = "a".repeat(255);

        // when
        var proposal = new TransactionProposal(
                USER_ID, " " + description + " ", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE);

        // then
        assertThat(proposal.getDescription()).isEqualTo(description);
    }

    @ParameterizedTest
    @MethodSource("invalidCreations")
    void should_throwIllegalArgument_when_creatingInvalidProposal(
            UserId userId,
            String description,
            Money amount,
            Category category,
            Instant createdAt,
            TransactionProposalTimeToLive timeToLive) {
        // when / then
        assertThatThrownBy(() -> new TransactionProposal(userId, description, amount, category, createdAt, timeToLive))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidCreations() {
        return Stream.of(
                Arguments.of(null, "Mercado", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, null, AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, "", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, "   ", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, "a".repeat(256), AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, " " + "a".repeat(256), AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, "Mercado", null, Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(
                        USER_ID, "Mercado", new Money(new BigDecimal("0.00")), Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(
                        USER_ID, "Mercado", new Money(new BigDecimal("-1.00")), Category.GROCERIES, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, "Mercado", AMOUNT, null, NOW, TIME_TO_LIVE),
                Arguments.of(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, null, TIME_TO_LIVE),
                Arguments.of(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, NOW, null));
    }

    @Test
    void should_buildTransactionWithProposalDataAndGivenInstant_when_convertedToTransaction() {
        // given
        var proposal = new TransactionProposal(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE);
        var confirmedAt = Instant.parse("2026-03-01T12:35:00.000Z");

        // when
        var transaction = proposal.toTransaction(confirmedAt);

        // then
        assertThat(transaction.getUserId()).isEqualTo(USER_ID);
        assertThat(transaction.getDescription()).isEqualTo("Mercado");
        assertThat(transaction.getAmount()).isEqualTo(AMOUNT);
        assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
        assertThat(transaction.getCreatedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void should_setExpiresAtToCreatedAtPlusTimeToLive_when_created() {
        // when
        var proposal = new TransactionProposal(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE);

        // then
        assertThat(proposal.getExpiresAt()).isEqualTo(Instant.parse("2026-03-01T12:35:45.123Z"));
    }

    @Test
    void should_beExpiredAtExactlyExpiresAt_when_checkedForPending() {
        // given
        var proposal = new TransactionProposal(USER_ID, "Mercado", AMOUNT, Category.GROCERIES, NOW, TIME_TO_LIVE);
        var expiresAt = proposal.getExpiresAt();

        // when / then
        assertThat(proposal.isPendingAt(NOW)).isTrue();
        assertThat(proposal.isPendingAt(expiresAt.minusMillis(1))).isTrue();
        assertThat(proposal.isPendingAt(expiresAt)).isFalse();
        assertThat(proposal.isPendingAt(expiresAt.plusMillis(1))).isFalse();
    }
}
