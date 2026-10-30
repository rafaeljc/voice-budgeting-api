package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dio.budgeting.application.input.ListTransactionsInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.PageQuery;
import dio.budgeting.domain.PageResult;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionCriterion.CategoryIs;
import dio.budgeting.domain.TransactionFilter;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.TransactionSort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListTransactionsUseCaseTest {

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private ListTransactionsUseCase useCase;

    @Test
    void should_passCriteriaToRepositoryAndMapPage_when_executed() {
        // given
        var filter = TransactionFilter.of(new CategoryIs(Category.GROCERIES));
        var sort = new TransactionSort(TransactionSort.Field.AMOUNT, TransactionSort.Direction.ASC);
        var page = new PageQuery(1, 2);
        var transaction = new Transaction(
                "Mercado",
                new Money(new BigDecimal("80.00")),
                Category.GROCERIES,
                Instant.parse("2026-03-01T12:30:45Z"));
        when(repository.findAll(filter, sort, page)).thenReturn(new PageResult<>(List.of(transaction), 1, 2, 3));

        // when
        var output = useCase.execute(new ListTransactionsInput(filter, sort, page));

        // then
        assertThat(output.number()).isEqualTo(1);
        assertThat(output.size()).isEqualTo(2);
        assertThat(output.totalElements()).isEqualTo(3);
        assertThat(output.totalPages()).isEqualTo(2);
        assertThat(output.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(transaction.getId().uuid().toString());
            assertThat(item.description()).isEqualTo("Mercado");
            assertThat(item.category()).isEqualTo("GROCERIES");
        });
    }
}
