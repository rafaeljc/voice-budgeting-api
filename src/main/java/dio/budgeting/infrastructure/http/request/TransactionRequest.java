package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransactionRequest(
        String description,
        Category category,

        @NotNull @Pattern(regexp = "^\\d{1,16}\\.\\d{2}$", message = "{transaction.amount.invalid}")
        String amount,

        @NotNull @Pattern(regexp = "^" + Money.CURRENCY + "$", message = "{transaction.currency.invalid}")
        String currency) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, new BigDecimal(amount), category);
    }
}
