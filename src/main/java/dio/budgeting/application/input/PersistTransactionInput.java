package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import java.math.BigDecimal;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionInput(
        @ToolParam(description = "Descrição do gasto") String description,

        @ToolParam(
                description = "Valor do gasto em reais, com até 2 casas decimais e ponto como separador (ex.: 80.00)")
        BigDecimal amount,

        @ToolParam(description = "Categoria de uma transação")
        Category category) {
    public Money toMoney() {
        return Money.of(amount);
    }
}
