package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionNotFoundException;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTransactionUseCaseTest {

    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private GetTransactionUseCase useCase;

    @Test
    void should_returnOutput_when_transactionExists() {
        // given
        var transaction = new Transaction(
                USER_ID,
                "Mercado",
                new Money(new BigDecimal("80.00")),
                Category.GROCERIES,
                Instant.parse("2026-03-01T12:30:45Z"));
        when(repository.findByIdAndUserId(transaction.getId(), USER_ID)).thenReturn(Optional.of(transaction));

        // when
        var output = useCase.execute(USER_ID, transaction.getId().uuid().toString());

        // then
        assertThat(output.id()).isEqualTo(transaction.getId().uuid().toString());
        assertThat(output.description()).isEqualTo("Mercado");
        assertThat(output.amount()).isEqualByComparingTo("80.00");
        assertThat(output.category()).isEqualTo("GROCERIES");
    }

    @Test
    void should_throwNotFound_when_transactionDoesNotExist() {
        // given
        var id = new TransactionId();
        when(repository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> useCase.execute(USER_ID, id.uuid().toString()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void should_throwNotFoundWithoutQuerying_when_idIsMalformed() {
        // when / then
        assertThatThrownBy(() -> useCase.execute(USER_ID, "abc")).isInstanceOf(TransactionNotFoundException.class);
        verifyNoInteractions(repository);
    }
}
