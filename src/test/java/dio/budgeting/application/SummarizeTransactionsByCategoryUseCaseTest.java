package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.TransactionSummary;
import dio.budgeting.domain.UserId;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummarizeTransactionsByCategoryUseCaseTest {

    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private SummarizeTransactionsByCategoryUseCase useCase;

    @Test
    void should_returnCategoryCountAndTotal_when_executed() {
        // given
        when(repository.summarizeByUserIdAndCategory(USER_ID, Category.GROCERIES))
                .thenReturn(new TransactionSummary(3, new Money(new BigDecimal("240.00"))));

        // when
        var output = useCase.execute(USER_ID, Category.GROCERIES);

        // then
        assertThat(output.category()).isEqualTo(Category.GROCERIES);
        assertThat(output.count()).isEqualTo(3);
        assertThat(output.total()).isEqualTo(new Money(new BigDecimal("240.00")));
    }

    @Test
    void should_returnZeroCountAndTotal_when_categoryHasNoTransactions() {
        // given
        when(repository.summarizeByUserIdAndCategory(USER_ID, Category.PHARMA))
                .thenReturn(new TransactionSummary(0, new Money(new BigDecimal("0.00"))));

        // when
        var output = useCase.execute(USER_ID, Category.PHARMA);

        // then
        assertThat(output.count()).isZero();
        assertThat(output.total()).isEqualTo(new Money(new BigDecimal("0.00")));
    }
}
