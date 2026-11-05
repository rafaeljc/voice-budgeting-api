package dio.budgeting.infrastructure.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dio.budgeting.application.SummarizeTransactionsByCategoryUseCase;
import dio.budgeting.application.input.ProposeTransactionInput;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.application.output.TransactionSummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.argument.ProposeTransactionToolArguments;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class TransactionToolsTest {
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @Mock
    private SummarizeTransactionsByCategoryUseCase summarizeUseCase;

    @InjectMocks
    private TransactionTools tools;

    private final InterpretationCollector collector = new InterpretationCollector();
    private final ToolContext toolContext = new ToolContext(
            Map.of(TransactionTools.USER_ID, USER_ID, TransactionTools.INTERPRETATION_COLLECTOR, collector));

    @Test
    void should_noteProposal_when_proposingTransaction() {
        // given
        var arguments = new ProposeTransactionToolArguments("Mercado", new BigDecimal("80.00"), Category.GROCERIES);

        // when
        tools.proposeTransaction(arguments, toolContext);

        // then
        assertThat(collector.interpretation())
                .isEqualTo(new AssistantOutput.Proposal(
                        new ProposeTransactionInput("Mercado", new BigDecimal("80.00"), Category.GROCERIES)));
    }

    @Test
    void should_noteSummaryOfAuthenticatedUser_when_summarizingCategory() {
        // given
        var summary = new TransactionSummaryOutput(Category.PHARMA, 2, new Money(new BigDecimal("55.50")));
        when(summarizeUseCase.execute(USER_ID, Category.PHARMA)).thenReturn(summary);

        // when
        tools.summarizeTransactionsByCategory(Category.PHARMA, toolContext);

        // then
        assertThat(collector.interpretation()).isEqualTo(new AssistantOutput.Summary(summary));
    }
}
