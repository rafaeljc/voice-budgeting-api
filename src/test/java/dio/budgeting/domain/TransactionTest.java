package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionTest {

    @Test
    void should_truncateCreatedAtToMillis_when_created() {
        // given
        var createdAt = Instant.parse("2026-03-01T12:30:45.123456789Z");

        // when
        var transaction = new Transaction("Mercado", new Money(new BigDecimal("80.00")), Category.GROCERIES, createdAt);

        // then
        assertThat(transaction.getCreatedAt()).isEqualTo(Instant.parse("2026-03-01T12:30:45.123Z"));
    }
}
