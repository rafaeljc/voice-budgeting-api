package dio.budgeting.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
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
                post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
                .andExpect(jsonPath("$.instance").value("/transactions"))
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
                post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body));

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
                post("/transactions").contentType(MediaType.APPLICATION_JSON).content("{\"description\": "));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:malformed-request"))
                .andExpect(jsonPath("$.title").value("Requisição malformada"))
                .andExpect(jsonPath("$.detail").value("O corpo da requisição não é um JSON válido."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void should_returnValidationProblemWithParameter_when_pathCategoryDoesNotExist() throws Exception {
        // when
        var response = mockMvc.perform(get("/transactions/FOO"));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.instance").value("/transactions/FOO"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].parameter").value("category"))
                .andExpect(jsonPath("$.errors[0].pointer").doesNotExist());
    }

    @Test
    void should_returnValidationProblemWithParameter_when_audioFileIsMissing() throws Exception {
        // when
        var response = mockMvc.perform(multipart("/transactions/ai"));

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
        var response = mockMvc.perform(delete("/transactions"));

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
                post("/transactions").contentType(MediaType.TEXT_PLAIN).content("x"));

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
        when(transactionRepository.findAllByCategory(any())).thenThrow(new RuntimeException(INTERNAL_MARKER));

        // when
        var response = mockMvc.perform(get("/transactions/GROCERIES"));

        // then
        var result = response.andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."))
                .andExpect(jsonPath("$.instance").value("/transactions/GROCERIES"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(INTERNAL_MARKER);
    }
}
