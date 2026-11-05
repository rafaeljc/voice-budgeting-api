package dio.budgeting.infrastructure.ai.tool.argument;

import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.domain.Category;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.ai.tool.annotation.ToolParam;

public record ProposeTransactionToolArguments(
        @NotBlank
        @Size(max = ProposeTransactionToolArguments.DESCRIPTION_MAX_LENGTH)
        @ToolParam(
                description =
                        "Descrição curta do gasto, em minúsculas e com até 100 caracteres, sem o valor nem a categoria"
                                + " (nomes próprios com maiúscula)")
        String description,

        @NotNull
        @Positive
        @Digits(integer = 16, fraction = 2)
        @ToolParam(
                description = "Valor do gasto em reais, com até 2 casas decimais e ponto como separador (ex.: 80.00)")
        BigDecimal amount,

        @NotNull @ToolParam(description = "Categoria de uma transação")
        Category category) {
    // Spoken aloud: 100 characters are about 5-6 s of speech, while the manual endpoint accepts up to 255
    public static final int DESCRIPTION_MAX_LENGTH = 100;

    public ProposeTransactionInput toInput() {
        return new ProposeTransactionInput(description, amount, category);
    }
}
