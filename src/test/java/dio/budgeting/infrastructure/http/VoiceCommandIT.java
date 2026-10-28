package dio.budgeting.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureAiStub
@ExtendWith(OutputCaptureExtension.class)
class VoiceCommandIT {

    private final MockMvc mockMvc;
    private final AiStub aiStub;
    private final TransactionRepository transactionRepository;

    VoiceCommandIT(MockMvc mockMvc, AiStub aiStub, TransactionRepository transactionRepository) {
        this.mockMvc = mockMvc;
        this.aiStub = aiStub;
        this.transactionRepository = transactionRepository;
    }

    @Test
    void should_persistTransactionAndReturnSpeech_when_commandRegistersExpense() throws Exception {
        // given
        var speech = new byte[] {1, 2, 3, 4};
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("persist-transaction", """
                {"input": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenAnswer("Registrei 80 reais em mercado.");
        aiStub.givenSpeech(speech);
        var audio = new MockMultipartFile(
                "file",
                "recording-1.m4a",
                "audio/mp4",
                Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a")));

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(audio));

        // then
        response.andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(content().bytes(speech));
        assertThat(transactionRepository.findAllByCategory(Category.GROCERIES))
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getDescription()).isEqualTo("Mercado");
                    assertThat(transaction.getAmount()).isEqualTo(new Money(new BigDecimal("80.00")));
                });
    }

    @Test
    void should_notLogTranscriptionNorAnswer_when_commandIsProcessed(CapturedOutput output) throws Exception {
        // given
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("persist-transaction", """
                {"input": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenAnswer("Registrei 80 reais em mercado.");
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});
        var audio = new MockMultipartFile(
                "file",
                "recording-1.m4a",
                "audio/mp4",
                Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a")));

        // when
        mockMvc.perform(multipart("/transactions/ai").file(audio)).andExpect(status().isOk());

        // then
        assertThat(output.getAll())
                .doesNotContain("gastei 80 reais no mercado")
                .doesNotContain("Registrei 80 reais em mercado.");
    }
}
