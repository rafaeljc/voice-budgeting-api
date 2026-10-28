package dio.budgeting.infrastructure.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class ErrorResponseIT {

    private final MockMvc mockMvc;

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
}
