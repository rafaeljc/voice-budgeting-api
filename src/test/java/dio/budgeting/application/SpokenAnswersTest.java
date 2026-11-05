package dio.budgeting.application;

import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.TransactionSummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpokenAnswersTest {
    // NumberFormat separates the symbol from the value with a no-break space
    private static final String NBSP = "\u00a0";

    @Test
    void should_askForConfirmationWithAmountDescriptionAndCategory_when_proposing() {
        // given
        var proposal = new ProposeTransactionInput("Pão e leite", new BigDecimal("80"), Category.GROCERIES);

        // when
        var answer = SpokenAnswers.proposal(proposal);

        // then
        assertThat(answer)
                .isEqualTo(
                        "Um gasto de R$" + NBSP + "80,00 com Pão e leite, na categoria mercado." + " Você confirma?");
    }

    @Test
    void should_groupThousandsAndKeepTwoDecimals_when_amountIsLarge() {
        // given
        var proposal = new ProposeTransactionInput("Pneus", new BigDecimal("1234.5"), Category.AUTO);

        // when
        var answer = SpokenAnswers.proposal(proposal);

        // then
        assertThat(answer).startsWith("Um gasto de R$" + NBSP + "1.234,50 com Pneus");
    }

    @ParameterizedTest
    @CsvSource({"GROCERIES,mercado", "PHARMA,farmácia", "AUTO,automóvel"})
    void should_speakCategoryName_when_proposing(Category category, String spoken) {
        // given
        var proposal = new ProposeTransactionInput("Algo", new BigDecimal("10"), category);

        // when
        var answer = SpokenAnswers.proposal(proposal);

        // then
        assertThat(answer).contains("na categoria " + spoken + ".");
    }

    @Test
    void should_useSingular_when_summaryHasOneTransaction() {
        // given
        var summary = new TransactionSummaryOutput(Category.GROCERIES, 1, new Money(new BigDecimal("80.00")));

        // when
        var answer = SpokenAnswers.summary(summary);

        // then
        assertThat(answer).isEqualTo("Você tem 1 gasto na categoria mercado, somando R$" + NBSP + "80,00.");
    }

    @Test
    void should_usePlural_when_summaryHasSeveralTransactions() {
        // given
        var summary = new TransactionSummaryOutput(Category.GROCERIES, 3, new Money(new BigDecimal("240.00")));

        // when
        var answer = SpokenAnswers.summary(summary);

        // then
        assertThat(answer).isEqualTo("Você tem 3 gastos na categoria mercado, somando R$" + NBSP + "240,00.");
    }

    @Test
    void should_saySpendingWasNotFound_when_summaryIsEmpty() {
        // given
        var summary = new TransactionSummaryOutput(Category.PHARMA, 0, new Money(new BigDecimal("0.00")));

        // when
        var answer = SpokenAnswers.summary(summary);

        // then
        assertThat(answer).isEqualTo("Você não tem gastos na categoria farmácia.");
    }

    @Test
    void should_explainWhatIsSupported_when_notUnderstood() {
        // when
        var answer = SpokenAnswers.notUnderstood();

        // then
        assertThat(answer).isEqualTo("Não entendi, tente novamente.");
    }
}
