package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.persistence.repository.TransactionProposalEntityRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiFailure;
import dio.budgeting.support.ai.AiStage;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.log.LogEvents;
import dio.budgeting.support.security.AccessTokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
    private final TransactionProposalEntityRepository proposalRepository;

    VoiceCommandIT(
            MockMvc mockMvc,
            AiStub aiStub,
            TransactionRepository transactionRepository,
            TransactionProposalEntityRepository proposalRepository) {
        this.mockMvc = mockMvc;
        this.aiStub = aiStub;
        this.transactionRepository = transactionRepository;
        this.proposalRepository = proposalRepository;
    }

    @Test
    void should_returnSpeechAndProposalWithoutCreatingTransaction_when_commandRegistersExpense() throws Exception {
        // given
        var speech = new byte[] {1, 2, 3, 4};
        givenExpenseCommand(speech);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        var result = response.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.speech.contentType").value("audio/mpeg"))
                .andExpect(
                        jsonPath("$.speech.content").value(Base64.getEncoder().encodeToString(speech)))
                .andExpect(jsonPath("$.transactionProposal.description").value("Mercado"))
                .andExpect(jsonPath("$.transactionProposal.amount").value("80.00"))
                .andExpect(jsonPath("$.transactionProposal.currency").value("BRL"))
                .andExpect(jsonPath("$.transactionProposal.category").value("GROCERIES"))
                .andReturn();
        var proposalId = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.transactionProposal.id");
        var expiresAt = Instant.parse(
                JsonPath.<String>read(result.getResponse().getContentAsString(), "$.transactionProposal.expiresAt"));
        assertThat(proposalRepository.findById(UUID.fromString(proposalId))).hasValueSatisfying(saved -> {
            assertThat(saved.getUserId()).isEqualTo(AccessTokens.DEFAULT_USER.uuid());
            assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plus(Duration.ofMinutes(5)));
        });
        assertThat(transactionRepository
                        .summarizeByUserIdAndCategory(AccessTokens.DEFAULT_USER, Category.GROCERIES)
                        .count())
                .isZero();
    }

    @Test
    void should_returnOnlySpeechAndSaveNothing_when_commandIsAQuery() throws Exception {
        // given
        var speech = new byte[] {1, 2, 3, 4};
        aiStub.givenTranscription("quanto gastei com mercado");
        aiStub.givenToolCall("list-transactions-by-category", """
                {"category": "GROCERIES"}
                """);
        aiStub.givenAnswer("Você não gastou nada com mercado.");
        aiStub.givenSpeech(speech);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.speech.content").value(Base64.getEncoder().encodeToString(speech)))
                .andExpect(jsonPath("$.transactionProposal").doesNotExist());
        assertThat(proposalRepository.count()).isZero();
    }

    @Test
    void should_keepOneProposal_when_modelRepeatsTheSameProposalInTheCommand() throws Exception {
        // given
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCalls(
                "propose-transaction",
                toolArguments("\"Mercado\"", "80.00", "\"GROCERIES\""),
                toolArguments("\" Mercado \"", "80", "\"GROCERIES\""));
        aiStub.givenAnswer("Entendi um gasto de 80 reais em mercado. Confirma?");
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionProposal.description").value("Mercado"))
                .andExpect(jsonPath("$.transactionProposal.amount").value("80.00"));
        assertThat(proposalRepository.count()).isEqualTo(1);
    }

    @Test
    void should_returnInvalidCommandProblemAndSaveNothing_when_modelProposesDifferentTransactions() throws Exception {
        // given
        aiStub.givenTranscription("gastei 80 no mercado e 30 na farmácia");
        aiStub.givenToolCalls(
                "propose-transaction",
                toolArguments("\"Mercado\"", "80.00", "\"GROCERIES\""),
                toolArguments("\"Farmácia\"", "30.00", "\"PHARMA\""));
        aiStub.givenAnswer("Entendi dois gastos. Confirma?");
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        assertThat(proposalRepository.count()).isZero();
    }

    @Test
    void should_acceptAndNameByDetectedFormat_when_recordingHasMisleadingNameAndContentType() throws Exception {
        // given
        givenExpenseCommand(new byte[] {1, 2, 3, 4});
        var misleading = new MockMultipartFile(
                "file", "gravacao.bin", "application/octet-stream", recording().getBytes());

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(misleading));

        // then
        response.andExpect(status().isOk());
        assertThat(aiStub.transcribedAudioFilename()).isEqualTo("audio.m4a");
    }

    @Test
    void should_proposeForTokenUserOnly_when_commandRegistersExpense() throws Exception {
        // given
        var tokenUser = new UserId(UUID.randomUUID());
        givenExpenseCommand(new byte[] {1, 2, 3, 4});

        // when
        mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .header(AUTHORIZATION, AccessTokens.authorization(tokenUser))
                        .file(recording()))
                .andExpect(status().isOk());

        // then
        assertThat(proposalRepository.findAll()).singleElement().satisfies(saved -> assertThat(saved.getUserId())
                .isEqualTo(tokenUser.uuid()));
    }

    @Test
    void should_notLogTranscriptionNorAnswer_when_commandIsProcessed(CapturedOutput output) throws Exception {
        // given
        givenExpenseCommand(new byte[] {1, 2, 3, 4});

        // when
        mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .header(AUTHORIZATION, AccessTokens.authorization())
                        .file(recording()))
                .andExpect(status().isOk());

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
        var requestId = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .header(AUTHORIZATION, AccessTokens.authorization())
                        .file(recording))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        // then
        assertEvent(output, requestId, "Voice command received", "audioSizeBytes", recording.getSize());
        assertEvent(output, requestId, "Voice command received", "audioFormat", "M4A");
        assertEvent(output, requestId, "Audio transcribed", "transcriptionLength", 26);
        assertEvent(output, requestId, "Assistant answered", "answerLength", 30);
        assertEvent(output, requestId, "Speech synthesized", "speechSizeBytes", 4);
    }

    @Test
    void should_logProposalCreatedWithIdOnly_when_commandProposesTransaction(CapturedOutput output) throws Exception {
        // given
        givenExpenseCommand(new byte[] {1, 2, 3, 4});

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .header(AUTHORIZATION, AccessTokens.authorization())
                        .file(recording()))
                .andReturn()
                .getResponse();

        // then
        var proposalId = JsonPath.<String>read(response.getContentAsString(), "$.transactionProposal.id");
        assertEvent(
                output,
                response.getHeader("X-Request-Id"),
                "Transaction proposal created",
                "transactionProposalId",
                proposalId);
        assertThat(output.getAll()).doesNotContain("Mercado").doesNotContain("Transaction persisted");
    }

    @Test
    void should_returnValidationProblemWithoutCallingAi_when_audioFileIsEmpty() throws Exception {
        // given
        var empty = new MockMultipartFile("file", "empty.m4a", "audio/mp4", new byte[0]);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(empty));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.errors[0].parameter").value("file"))
                .andExpect(jsonPath("$.errors[0].detail").value("O arquivo está vazio."));
    }

    @Test
    void should_returnValidationProblemWithoutCallingAi_when_audioFileIsNotSupportedAudio() throws Exception {
        // given
        var pdf = new MockMultipartFile(
                "file", "recording.m4a", "audio/mp4", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34});

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(pdf));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.errors[0].parameter").value("file"))
                .andExpect(jsonPath("$.errors[0].detail").value("Envie o áudio em M4A, MP3 ou WAV."));
    }

    @ParameterizedTest
    @MethodSource("transientFailures")
    void should_returnServiceUnavailableWithRetryAfter_when_providerFailureIsTransient(AiStage stage, AiFailure failure)
            throws Exception {
        // given
        givenStagesBefore(stage);
        aiStub.givenFailure(stage, failure);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:assistant-unavailable"))
                .andExpect(jsonPath("$.title").value("Assistente indisponível"))
                .andExpect(jsonPath("$.detail")
                        .value("O assistente está temporariamente indisponível. Tente novamente em instantes."))
                .andExpect(jsonPath("$.instance").value(VOICE_COMMAND_PATH));
    }

    @Test
    void should_returnServiceUnavailableAndSaveNoProposal_when_speechSynthesisFails() throws Exception {
        // given
        givenExpenseCommand(new byte[] {1, 2, 3, 4});
        aiStub.givenFailure(AiStage.SPEECH, AiFailure.PROVIDER_ERROR);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:assistant-unavailable"));
        assertThat(proposalRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(AiStage.class)
    void should_returnGenericInternalError_when_providerRejectsRequest(AiStage stage) throws Exception {
        // given
        givenStagesBefore(stage);
        aiStub.givenFailure(stage, AiFailure.REQUEST_REJECTED);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

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
        aiStub.givenFailure(AiStage.TRANSCRIPTION, AiFailure.RATE_LIMITED);

        // when
        var requestId = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .header(AUTHORIZATION, AccessTokens.authorization())
                        .file(recording()))
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
        aiStub.givenFailure(AiStage.TRANSCRIPTION, AiFailure.REQUEST_REJECTED);

        // when
        mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        var event = LogEvents.withMessage(output, "Unexpected error");
        assertThat(event.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThat(event.path("error").path("stack_trace").asString()).contains("UnauthorizedException");
    }

    @Test
    void should_returnInvalidCommandProblem_when_toolAmountIsNegative() throws Exception {
        // given
        givenProposeCommand("""
                {"arguments": {"description": "Mercado", "amount": -10, "category": "GROCERIES"}}
                """);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"))
                .andExpect(jsonPath("$.title").value("Comando não processado"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Não foi possível processar o comando com os dados entendidos."
                                + " Envie um novo comando de voz."))
                .andExpect(jsonPath("$.instance").value(VOICE_COMMAND_PATH))
                .andExpect(jsonPath("$.errors").doesNotExist());
        assertThat(proposalRepository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidToolArguments")
    void should_returnInvalidCommandProblem_when_toolArgumentsAreInvalid(String arguments) throws Exception {
        // given
        givenProposeCommand(arguments);

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"));
        assertThat(proposalRepository.count()).isZero();
    }

    @Test
    void should_returnInvalidCommandProblemWithoutLoggingValue_when_toolCategoryDoesNotExist(CapturedOutput output)
            throws Exception {
        // given
        givenProposeCommand(toolArguments("\"Mercado\"", "80.00", "\"FOO\""));

        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .file(recording()));

        // then
        response.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:invalid-command"));
        assertThat(output.getAll()).doesNotContain("FOO");
    }

    @Test
    void should_logRejectedRequestWithInvalidFields_when_toolArgumentsAreInvalid(CapturedOutput output)
            throws Exception {
        // given
        givenProposeCommand(toolArguments("\"Mercado\"", "-10", "\"GROCERIES\""));

        // when
        var requestId = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .header(AUTHORIZATION, AccessTokens.authorization())
                        .file(recording()))
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
            when(failingRepository.summarizeByUserIdAndCategory(any(), any()))
                    .thenThrow(new RuntimeException(INTERNAL_MARKER));
            aiStub.givenTranscription("quanto gastei com mercado");
            aiStub.givenToolCall("list-transactions-by-category", """
                    {"category": "GROCERIES"}
                    """);

            // when
            var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                    .header(AUTHORIZATION, AccessTokens.authorization())
                    .file(recording()));

            // then
            response.andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.title").value("Erro interno"))
                    .andExpect(content().string(not(containsString(INTERNAL_MARKER))));
        }
    }

    static Stream<Arguments> transientFailures() {
        var failures = EnumSet.of(AiFailure.CONNECTION_FAILURE, AiFailure.RATE_LIMITED, AiFailure.PROVIDER_ERROR);
        return Arrays.stream(AiStage.values())
                .flatMap(stage -> failures.stream().map(failure -> Arguments.of(stage, failure)));
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

    // The stages before the failing one succeed, so the failure is the one under test
    private void givenStagesBefore(AiStage stage) {
        if (stage != AiStage.TRANSCRIPTION) {
            aiStub.givenTranscription("gastei 80 reais no mercado");
        }
        if (stage == AiStage.SPEECH) {
            aiStub.givenAnswer("Registrei 80 reais em mercado.");
        }
    }

    private void givenProposeCommand(String argumentsJson) {
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("propose-transaction", argumentsJson);
        aiStub.givenAnswer("Registrei 80 reais em mercado.");
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});
    }

    private void givenExpenseCommand(byte[] speech) {
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("propose-transaction", """
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
