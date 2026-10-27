package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import java.math.BigDecimal;

public record TransactionRequest(String description, Category category, String amount, String currency) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, new BigDecimal(amount), category);
    }
}
