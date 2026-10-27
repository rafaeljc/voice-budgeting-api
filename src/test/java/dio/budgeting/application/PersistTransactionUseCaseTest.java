package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistTransactionUseCaseTest {

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private PersistTransactionUseCase useCase;

    @Test
    void should_saveTransactionWithInputData_when_executed() {
        // given
        var input = new PersistTransactionInput("Mercado", new BigDecimal("80"), Category.GROCERIES);
        when(repository.save(any(Transaction.class))).then(returnsFirstArg());

        // when
        var output = useCase.execute(input);

        // then
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("Mercado");
        assertThat(saved.getValue().getAmount()).isEqualTo(new Money(new BigDecimal("80.00")));
        assertThat(saved.getValue().getCategory()).isEqualTo(Category.GROCERIES);
        assertThat(output.id()).isEqualTo(saved.getValue().getId().uuid().toString());
    }
}
