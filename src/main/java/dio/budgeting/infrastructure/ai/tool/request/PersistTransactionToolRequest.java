package dio.budgeting.infrastructure.ai.tool.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionToolRequest(
        @NotBlank @Size(max = Transaction.DESCRIPTION_MAX_LENGTH) @ToolParam(description = "Descrição do gasto")
        String description,

        @NotNull
        @Positive
        @Digits(integer = 16, fraction = 2)
        @ToolParam(
                description = "Valor do gasto em reais, com até 2 casas decimais e ponto como separador (ex.: 80.00)")
        BigDecimal amount,

        @NotNull @ToolParam(description = "Categoria de uma transação")
        Category category) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category);
    }
}
