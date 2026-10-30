package dio.budgeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageResultTest {

    @ParameterizedTest
    @CsvSource({"0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "41, 20, 3"})
    void should_computeTotalPages_when_totalElementsAndSizeAreKnown(long totalElements, int size, int expected) {
        // given
        var page = new PageResult<>(List.of(), 0, size, totalElements);

        // when
        var totalPages = page.totalPages();

        // then
        assertThat(totalPages).isEqualTo(expected);
    }

    @Test
    void should_keepMetadata_when_mappingContent() {
        // given
        var page = new PageResult<>(List.of(1, 2), 3, 2, 8);

        // when
        var mapped = page.map(number -> "n" + number);

        // then
        assertThat(mapped.content()).containsExactly("n1", "n2");
        assertThat(mapped.number()).isEqualTo(3);
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.totalElements()).isEqualTo(8);
    }

    @Test
    void should_rejectNegativeNumber_when_creatingPageQuery() {
        assertThatThrownBy(() -> new PageQuery(-1, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_rejectSizeBelowOne_when_creatingPageQuery() {
        assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
