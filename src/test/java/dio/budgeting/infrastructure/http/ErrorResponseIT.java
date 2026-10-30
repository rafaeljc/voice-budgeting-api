package dio.budgeting.infrastructure.http;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceDocumentation;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.log.LogEvents;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@ExtendWith(OutputCaptureExtension.class)
class ErrorResponseIT {

    private static final String INTERNAL_MARKER = "internal-marker-db-down";

    private final MockMvc mockMvc;

    @MockitoBean
    private TransactionRepository transactionRepository;

    ErrorResponseIT(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_returnValidationProblem_when_amountIsInvalid() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80,00", "currency": "BRL"}
                """;

        // when
        var response = mockMvc.perform(
                post(TRANSACTIONS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
                .andExpect(jsonPath("$.instance").value(TRANSACTIONS_PATH))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].pointer").value("#/amount"))
                .andExpect(jsonPath("$.errors[0].detail")
                        .value("O valor deve ter o formato 0.00, com ponto e duas casas decimais."));
    }

    @Test
    void should_returnValidationProblemWithPointer_when_categoryDoesNotExist() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "FOO", "amount": "80.00", "currency": "BRL"}
                """;

        // when
        var response = mockMvc.perform(
                post(TRANSACTIONS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].pointer").value("#/category"))
                .andExpect(jsonPath("$.errors[0].detail").value("Valor inválido."));
    }

    @Test
    void should_returnMalformedRequestProblemWithoutErrors_when_jsonIsMalformed() throws Exception {
        // when
        var response = mockMvc.perform(
                post(TRANSACTIONS_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"description\": "));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:malformed-request"))
                .andExpect(jsonPath("$.title").value("Requisição malformada"))
                .andExpect(jsonPath("$.detail").value("O corpo da requisição não é um JSON válido."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("invalidQueryParameters")
    void should_returnValidationProblemWithParameter_when_queryParameterIsInvalid(
            String parameter, String value, String detail) throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param(parameter, value));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.instance").value(TRANSACTIONS_PATH))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].parameter").value(parameter))
                .andExpect(jsonPath("$.errors[0].detail").value(detail))
                .andExpect(jsonPath("$.errors[0].pointer").doesNotExist())
                .andDo(document(
                        "list-transactions-invalid-parameter",
                        ResourceDocumentation.resource(ResourceSnippetParameters.builder()
                                .tag("Transações")
                                .summary("Lista as transações")
                                .responseFields(
                                        fieldWithPath("type").description("Tipo do problema."),
                                        fieldWithPath("title").description("Resumo do problema."),
                                        fieldWithPath("status").description("Status HTTP."),
                                        fieldWithPath("detail").description("Explicação do problema."),
                                        fieldWithPath("instance").description("Caminho da requisição."),
                                        fieldWithPath("errors[]").description("Parâmetros inválidos."),
                                        fieldWithPath("errors[].parameter")
                                                .description("Nome do parâmetro de consulta inválido."),
                                        fieldWithPath("errors[].detail")
                                                .description("Motivo pelo qual o parâmetro é inválido."))
                                .build())));
    }

    static Stream<Arguments> invalidQueryParameters() {
        var invalidValue = "Valor inválido.";
        var invalidSort =
                "A ordenação deve seguir o padrão \"campo,direção\" (por exemplo \"amount,desc\"), com campo createdAt ou amount e direção asc ou desc.";
        return Stream.of(
                Arguments.of("category", "FOO", invalidValue),
                Arguments.of("sort", "description,asc", invalidSort),
                Arguments.of("sort", "amount", invalidSort),
                Arguments.of("size", "abc", invalidValue));
    }

    @Test
    void should_logRejectedRequestWithParameterNameOnly_when_queryParameterIsInvalid(CapturedOutput output)
            throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param("size", "secret-value"))
                .andReturn()
                .getResponse();

        // then
        var event = LogEvents.withMessage(output, "Request rejected");
        assertThat(event.path("status").asInt()).isEqualTo(400);
        assertThat(event.path("invalidFields")).singleElement().satisfies(field -> assertThat(field.asString())
                .isEqualTo("size"));
        assertThat(event.path("traceId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(output.getAll()).doesNotContain("secret-value");
    }

    @Test
    void should_returnValidationProblemWithParameter_when_audioFileIsMissing() throws Exception {
        // when
        var response = mockMvc.perform(multipart(VOICE_COMMAND_PATH));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.errors[0].parameter").value("file"))
                .andExpect(jsonPath("$.errors[0].detail").value("Campo obrigatório ausente."));
    }

    @Test
    void should_returnBlankTypeProblemInPortuguese_when_routeDoesNotExist() throws Exception {
        // when
        var response = mockMvc.perform(get("/nowhere"));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("O recurso solicitado não existe."))
                .andExpect(jsonPath("$.instance").value("/nowhere"));
    }

    @Test
    void should_returnBlankTypeProblemInPortuguese_when_methodIsNotSupported() throws Exception {
        // when
        var response = mockMvc.perform(delete(TRANSACTIONS_PATH));

        // then
        response.andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Método não permitido"))
                .andExpect(jsonPath("$.detail").value("O método HTTP não é suportado para este recurso."));
    }

    @Test
    void should_returnBlankTypeProblemInPortuguese_when_contentTypeIsNotSupported() throws Exception {
        // when
        var response = mockMvc.perform(
                post(TRANSACTIONS_PATH).contentType(MediaType.TEXT_PLAIN).content("x"));

        // then
        response.andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Tipo de mídia não suportado"))
                .andExpect(jsonPath("$.detail").value("O tipo de mídia da requisição não é suportado."));
    }

    @Test
    void should_returnGenericProblem_when_unexpectedFailureOccurs() throws Exception {
        // given
        when(transactionRepository.findAll(any(), any(), any())).thenThrow(new RuntimeException(INTERNAL_MARKER));

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH));

        // then
        var result = response.andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."))
                .andExpect(jsonPath("$.instance").value(TRANSACTIONS_PATH))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(INTERNAL_MARKER);
    }

    @Test
    void should_logRejectedRequestWithFieldNamesOnly_when_validationFails(CapturedOutput output) throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "secret-value", "currency": "BRL"}
                """;

        // when
        var response = mockMvc.perform(post(TRANSACTIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();

        // then
        var event = LogEvents.withMessage(output, "Request rejected");
        assertThat(event.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(event.path("status").asInt()).isEqualTo(400);
        assertThat(event.path("problemType").asString()).isEqualTo("urn:problem-type:budgeting:validation-error");
        assertThat(event.path("invalidFields")).singleElement().satisfies(field -> assertThat(field.asString())
                .isEqualTo("#/amount"));
        assertThat(event.path("traceId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(output.getAll()).doesNotContain("secret-value");
    }

    @Test
    void should_logRejectedRequestWithProblemType_when_transactionIsNotFound(CapturedOutput output) throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH + "/" + UUID.randomUUID()))
                .andReturn()
                .getResponse();

        // then
        var event = LogEvents.withMessage(output, "Request rejected");
        assertThat(event.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(event.path("status").asInt()).isEqualTo(404);
        assertThat(event.path("problemType").asString()).isEqualTo("urn:problem-type:budgeting:transaction-not-found");
        assertThat(event.path("traceId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
    }

    @Test
    void should_logUnexpectedErrorWithStackTrace_when_unexpectedFailureOccurs(CapturedOutput output) throws Exception {
        // given
        when(transactionRepository.findAll(any(), any(), any())).thenThrow(new RuntimeException(INTERNAL_MARKER));

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH)).andReturn().getResponse();

        // then
        var event = LogEvents.withMessage(output, "Unexpected error");
        assertThat(event.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThat(event.path("error").path("stack_trace").asString()).contains("RuntimeException");
        assertThat(event.path("traceId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
    }
}
