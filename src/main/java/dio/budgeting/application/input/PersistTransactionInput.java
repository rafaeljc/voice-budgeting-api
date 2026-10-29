package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import java.math.BigDecimal;

public record PersistTransactionInput(String description, BigDecimal amount, Category category) {
    public Money toMoney() {
        return Money.of(amount);
    }
}
