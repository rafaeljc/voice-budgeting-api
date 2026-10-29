package dio.budgeting.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiFailure;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.log.LogEvents;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
        givenExpenseCommand(speech);

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

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
        givenExpenseCommand(new byte[] {1, 2, 3, 4});

        // when
        mockMvc.perform(multipart("/transactions/ai").file(recording())).andExpect(status().isOk());

        // then
        assertThat(output.getAll())
                .doesNotContain("gastei 80 reais no mercado")
                .doesNotContain("Registrei 80 reais em mercado.");
    }

    @Test
    void should_logFlowEventsWithTraceId_when_commandIsProcessed(CapturedOutput output) throws Exception {
        // given
        givenExpenseCommand(new byte[] {1, 2, 3, 4});
        var recording = recording();

        // when
        var requestId = mockMvc.perform(multipart("/transactions/ai").file(recording))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        // then
        var transactionId = transactionRepository
                .findAllByCategory(Category.GROCERIES)
                .getFirst()
                .getId()
                .uuid()
                .toString();
        assertEvent(output, requestId, "Voice command received", "audioSizeBytes", recording.getSize());
        assertEvent(output, requestId, "Voice command received", "audioContentType", "audio/mp4");
        assertEvent(output, requestId, "Audio transcribed", "transcriptionLength", 26);
        assertEvent(output, requestId, "Assistant answered", "answerLength", 30);
        assertEvent(output, requestId, "Speech synthesized", "speechSizeBytes", 4);
        assertEvent(output, requestId, "Transaction persisted", "transactionId", transactionId);
    }

    @ParameterizedTest
    @EnumSource(
            value = AiFailure.class,
            names = {"CONNECTION_FAILURE", "RATE_LIMITED", "PROVIDER_ERROR"})
    void should_returnServiceUnavailableWithRetryAfter_when_providerFailureIsTransient(AiFailure failure)
            throws Exception {
        // given
        aiStub.givenTranscriptionFailure(failure);

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

        // then
        response.andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:assistant-unavailable"))
                .andExpect(jsonPath("$.title").value("Assistente indisponível"))
                .andExpect(jsonPath("$.detail")
                        .value("O assistente está temporariamente indisponível. Tente novamente em instantes."))
                .andExpect(jsonPath("$.instance").value("/transactions/ai"));
    }

    @Test
    void should_returnGenericInternalError_when_providerRejectsRequest() throws Exception {
        // given
        aiStub.givenTranscriptionFailure(AiFailure.REQUEST_REJECTED);

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

        // then
        response.andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."));
    }

    @Test
    void should_logProviderUnavailableAsWarning_when_providerFailsTransiently(CapturedOutput output) throws Exception {
        // given
        aiStub.givenTranscriptionFailure(AiFailure.RATE_LIMITED);

        // when
        var requestId = mockMvc.perform(multipart("/transactions/ai").file(recording()))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        // then
        var event = LogEvents.withMessage(output, "AI provider unavailable");
        assertThat(event.path("log").path("level").asString()).isEqualTo("WARN");
        assertThat(event.path("status").asInt()).isEqualTo(503);
        assertThat(event.path("providerStatus").asInt()).isEqualTo(429);
        assertThat(event.path("traceId").asString()).isEqualTo(requestId);
        assertThat(event.has("error")).isFalse();
    }

    @Test
    void should_logUnexpectedErrorWithStackTrace_when_providerFailsDefinitively(CapturedOutput output)
            throws Exception {
        // given
        aiStub.givenTranscriptionFailure(AiFailure.REQUEST_REJECTED);

        // when
        mockMvc.perform(multipart("/transactions/ai").file(recording()));

        // then
        var event = LogEvents.withMessage(output, "Unexpected error");
        assertThat(event.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThat(event.path("error").path("stack_trace").asString()).contains("UnauthorizedException");
    }

    @Test
    void should_returnInvalidCommandProblem_when_toolAmountIsNegative() throws Exception {
        // given
        givenPersistCommand("""
                {"arguments": {"description": "Mercado", "amount": -10, "category": "GROCERIES"}}
                """);

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"))
                .andExpect(jsonPath("$.title").value("Comando não processado"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Não foi possível processar o comando com os dados entendidos."
                                + " Envie um novo comando de voz."))
                .andExpect(jsonPath("$.instance").value("/transactions/ai"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        assertThat(transactionRepository.findAllByCategory(Category.GROCERIES)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidToolArguments")
    void should_returnInvalidCommandProblem_when_toolArgumentsAreInvalid(String arguments) throws Exception {
        // given
        givenPersistCommand(arguments);

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"));
        assertThat(transactionRepository.findAllByCategory(Category.GROCERIES)).isEmpty();
    }

    @Test
    void should_returnInvalidCommandProblemWithoutLoggingValue_when_toolCategoryDoesNotExist(CapturedOutput output)
            throws Exception {
        // given
        givenPersistCommand(toolArguments("\"Mercado\"", "80.00", "\"FOO\""));

        // when
        var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"));
        assertThat(output.getAll()).doesNotContain("FOO");
    }

    @Test
    void should_logRejectedRequestWithInvalidFields_when_toolArgumentsAreInvalid(CapturedOutput output)
            throws Exception {
        // given
        givenPersistCommand(toolArguments("\"Mercado\"", "-10", "\"GROCERIES\""));

        // when
        var requestId = mockMvc.perform(multipart("/transactions/ai").file(recording()))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        // then
        var event = LogEvents.withMessage(output, "Request rejected");
        assertThat(event.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(event.path("status").asInt()).isEqualTo(422);
        assertThat(event.path("problemType").asString()).isEqualTo("urn:problem-type:budgeting:invalid-command");
        assertThat(event.path("invalidFields").values())
                .extracting(node -> node.asString())
                .containsExactly("amount");
        assertThat(event.path("traceId").asString()).isEqualTo(requestId);
    }

    @Nested
    class WhenToolFailsUnexpectedly {
        private static final String INTERNAL_MARKER = "internal-marker-db-down";

        @MockitoBean
        private TransactionRepository failingRepository;

        @Test
        void should_returnGenericInternalError_when_toolFailsUnexpectedly() throws Exception {
            // given
            when(failingRepository.save(any())).thenThrow(new RuntimeException(INTERNAL_MARKER));
            givenPersistCommand(toolArguments("\"Mercado\"", "80.00", "\"GROCERIES\""));

            // when
            var response = mockMvc.perform(multipart("/transactions/ai").file(recording()));

            // then
            response.andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.title").value("Erro interno"))
                    .andExpect(content().string(not(containsString(INTERNAL_MARKER))));
        }
    }

    static Stream<String> invalidToolArguments() {
        return Stream.of(
                toolArguments("\"\"", "80.00", "\"GROCERIES\""),
                toolArguments("\"" + "a".repeat(256) + "\"", "80.00", "\"GROCERIES\""),
                toolArguments("\"Mercado\"", "80.005", "\"GROCERIES\""),
                toolArguments("\"Mercado\"", "0", "\"GROCERIES\""),
                """
                {"arguments": {"description": "Mercado", "amount": 80.00}}
                """);
    }

    private static String toolArguments(String description, String amount, String category) {
        return "{\"arguments\": {\"description\": %s, \"amount\": %s, \"category\": %s}}"
                .formatted(description, amount, category);
    }

    private void givenPersistCommand(String argumentsJson) {
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("persist-transaction", argumentsJson);
        aiStub.givenAnswer("Registrei 80 reais em mercado.");
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});
    }

    private void givenExpenseCommand(byte[] speech) {
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("persist-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenAnswer("Registrei 80 reais em mercado.");
        aiStub.givenSpeech(speech);
    }

    private static MockMultipartFile recording() throws IOException {
        return new MockMultipartFile(
                "file",
                "recording-1.m4a",
                "audio/mp4",
                Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a")));
    }

    private static void assertEvent(
            CapturedOutput output, String requestId, String message, String field, Object expected) {
        var event = LogEvents.withMessage(output, message);
        assertThat(event.path("traceId").asString()).isEqualTo(requestId);
        assertThat(event.path(field).asString()).isEqualTo(String.valueOf(expected));
    }
}
