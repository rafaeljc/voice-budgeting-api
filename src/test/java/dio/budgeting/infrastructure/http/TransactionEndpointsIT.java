package dio.budgeting.infrastructure.http;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceDocumentation;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jayway.jsonpath.JsonPath;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class TransactionEndpointsIT {

    private final MockMvc mockMvc;
    private final TransactionEntityRepository repository;

    TransactionEndpointsIT(MockMvc mockMvc, TransactionEntityRepository repository) {
        this.mockMvc = mockMvc;
        this.repository = repository;
    }

    @Test
    void should_returnCreatedTransaction_when_listingByCategory() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}
                """;
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(document(
                        "create-transaction",
                        ResourceDocumentation.resource(ResourceSnippetParameters.builder()
                                .tag("Transações")
                                .summary("Cria uma transação")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("80.00"))
                .andExpect(jsonPath("$.currency").value("BRL"));

        // when
        var response = mockMvc.perform(get("/transactions/GROCERIES"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].description").value("Mercado"))
                .andExpect(jsonPath("$[0].category").value("GROCERIES"))
                .andExpect(jsonPath("$[0].amount").value("80.00"))
                .andExpect(jsonPath("$[0].currency").value("BRL"));
    }

    @Test
    void should_returnSameCreatedAtInUtc_when_creatingAndListing() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}
                """;
        var before = Instant.now();

        // when
        var created = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        var after = Instant.now();

        // then
        var createdAt = JsonPath.<String>read(created.getResponse().getContentAsString(), "$.createdAt");
        assertThat(createdAt).endsWith("Z");
        assertThat(Instant.parse(createdAt)).isBetween(before.truncatedTo(ChronoUnit.MILLIS), after);
        mockMvc.perform(get("/transactions/GROCERIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdAt").value(createdAt));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void should_returnBadRequestAndSaveNothing_when_amountOrCurrencyIsInvalid(String body) throws Exception {
        // when
        var response = mockMvc.perform(
                post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        response.andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void should_returnValidationErrorAndSaveNothing_when_fieldViolatesBusinessRule(
            String body, String pointer, String detail) throws Exception {
        // when
        var response = mockMvc.perform(
                post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:validation-error"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].pointer").value(pointer))
                .andExpect(jsonPath("$.errors[0].detail").value(detail));
        assertThat(repository.count()).isZero();
    }

    static Stream<Arguments> invalidFields() {
        var required = "A descrição é obrigatória.";
        return Stream.of(
                Arguments.of(bodyOf(null, "\"GROCERIES\"", "\"80.00\""), "#/description", required),
                Arguments.of(bodyOf("\"\"", "\"GROCERIES\"", "\"80.00\""), "#/description", required),
                Arguments.of(bodyOf("\"   \"", "\"GROCERIES\"", "\"80.00\""), "#/description", required),
                Arguments.of(
                        bodyOf("\"" + "a".repeat(256) + "\"", "\"GROCERIES\"", "\"80.00\""),
                        "#/description",
                        "A descrição deve ter no máximo 255 caracteres."),
                Arguments.of(bodyOf("\"Mercado\"", null, "\"80.00\""), "#/category", "A categoria é obrigatória."),
                Arguments.of(
                        bodyOf("\"Mercado\"", "\"GROCERIES\"", "\"0.00\""),
                        "#/amount",
                        "O valor deve ser maior que zero."),
                Arguments.of(
                        bodyOf("\"Mercado\"", "\"GROCERIES\"", "\"-10.00\""),
                        "#/amount",
                        "O valor deve ser maior que zero."));
    }

    private static String bodyOf(String description, String category, String amount) {
        var descriptionField = description == null ? "" : "\"description\": " + description + ", ";
        var categoryField = category == null ? "" : "\"category\": " + category + ", ";
        return "{" + descriptionField + categoryField + "\"amount\": " + amount + ", \"currency\": \"BRL\"}";
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
                body("\"80,00\"", "\"BRL\""),
                body("\"80.5\"", "\"BRL\""),
                body("\"80\"", "\"BRL\""),
                body("\"abc\"", "\"BRL\""),
                body("\"-10.00\"", "\"BRL\""),
                body("\"12345678901234567.00\"", "\"BRL\""),
                body("\"80.00\"", "\"USD\""),
                body(null, "\"BRL\""),
                body("\"80.00\"", null));
    }

    private static String body(String amount, String currency) {
        var amountField = amount == null ? "" : ", \"amount\": " + amount;
        var currencyField = currency == null ? "" : ", \"currency\": " + currency;
        return "{\"description\": \"Mercado\", \"category\": \"GROCERIES\"" + amountField + currencyField + "}";
    }
}
