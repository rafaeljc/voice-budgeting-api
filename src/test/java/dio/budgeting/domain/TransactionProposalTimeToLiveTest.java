package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionProposalTimeToLiveTest {

    @Test
    void should_keepValue_when_positive() {
        // when
        var timeToLive = new TransactionProposalTimeToLive(Duration.ofMinutes(5));

        // then
        assertThat(timeToLive.value()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void should_rejectNull_when_created() {
        // when / then
        assertThatThrownBy(() -> new TransactionProposalTimeToLive(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -300})
    void should_rejectValue_when_zeroOrNegative(long seconds) {
        // when / then
        assertThatThrownBy(() -> new TransactionProposalTimeToLive(Duration.ofSeconds(seconds)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
