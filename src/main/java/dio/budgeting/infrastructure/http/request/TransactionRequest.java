package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank(message = "{transaction.description.required}")
        @Size(max = Transaction.DESCRIPTION_MAX_LENGTH, message = "{transaction.description.too-long}")
        String description,

        @NotNull(message = "{transaction.category.required}")
        Category category,

        @NotNull
        @Pattern(regexp = "^-?\\d{1,16}\\.\\d{2}$", message = "{transaction.amount.invalid}")
        @Pattern(regexp = "^(?!(-\\d{1,16}|0{1,16})\\.\\d{2}$).*$", message = "{transaction.amount.positive}")
        String amount,

        @NotNull @Pattern(regexp = "^" + Money.CURRENCY + "$", message = "{transaction.currency.invalid}")
        String currency) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, new BigDecimal(amount), category);
    }
}
