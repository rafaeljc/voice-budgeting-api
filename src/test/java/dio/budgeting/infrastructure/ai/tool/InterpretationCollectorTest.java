package dio.budgeting.infrastructure.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.application.output.TransactionSummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InterpretationCollectorTest {
    private static final AssistantOutput PROPOSAL = new AssistantOutput.Proposal(
            new ProposeTransactionInput("Mercado", new BigDecimal("80.00"), Category.GROCERIES));
    private static final AssistantOutput SUMMARY = new AssistantOutput.Summary(
            new TransactionSummaryOutput(Category.GROCERIES, 3, new Money(new BigDecimal("240.00"))));

    private final InterpretationCollector collector = new InterpretationCollector();

    @Test
    void should_beNotUnderstood_when_nothingWasNoted() {
        // when
        var interpretation = collector.interpretation();

        // then
        assertThat(interpretation).isEqualTo(new AssistantOutput.NotUnderstood());
    }

    @Test
    void should_returnNotedInterpretation_when_noted() {
        // when
        collector.note(SUMMARY);

        // then
        assertThat(collector.interpretation()).isSameAs(SUMMARY);
    }

    @Test
    void should_refuseSecondNote_when_itIsTheSame() {
        // given
        collector.note(PROPOSAL);

        // when / then
        assertThatThrownBy(() -> collector.note(PROPOSAL)).isInstanceOf(MultipleToolCallsException.class);
    }

    @Test
    void should_refuseSecondNote_when_itIsFromAnotherTool() {
        // given
        collector.note(PROPOSAL);

        // when / then
        assertThatThrownBy(() -> collector.note(SUMMARY)).isInstanceOf(MultipleToolCallsException.class);
    }
}
