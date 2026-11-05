package dio.budgeting.application;

import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.TransactionSummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import java.text.NumberFormat;
import java.util.Locale;

/** Fixed spoken answers: the same intent always yields the same sentence, and the model never writes them. */
final class SpokenAnswers {
    private static final Locale PORTUGUESE_BRAZIL = Locale.of("pt", "BR");

    private SpokenAnswers() {}

    static String proposal(ProposeTransactionInput proposal) {
        return "Um gasto de %s com %s, na categoria %s. Você confirma?"
                .formatted(formatted(proposal.toMoney()), proposal.description(), spoken(proposal.category()));
    }

    static String summary(TransactionSummaryOutput summary) {
        var category = spoken(summary.category());
        if (summary.count() == 0) {
            return "Você não tem gastos na categoria %s.".formatted(category);
        }
        var expenses = summary.count() == 1 ? "gasto" : "gastos";
        return "Você tem %d %s na categoria %s, somando %s."
                .formatted(summary.count(), expenses, category, formatted(summary.total()));
    }

    static String notUnderstood() {
        return "Não entendi, tente novamente.";
    }

    // NumberFormat is not thread-safe, so each call gets its own
    private static String formatted(Money money) {
        return NumberFormat.getCurrencyInstance(PORTUGUESE_BRAZIL).format(money.value());
    }

    // No default: a new category must not compile until it has a spoken name
    private static String spoken(Category category) {
        return switch (category) {
            case GROCERIES -> "mercado";
            case PHARMA -> "farmácia";
            case AUTO -> "automóvel";
        };
    }
}
