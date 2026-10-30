package dio.budgeting.infrastructure.http;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceDocumentation;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class TransactionEndpointsIT {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANCE_PATTERN = Pattern.compile("\"instance\":\"[^\"]*\"");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z");

    private final MockMvc mockMvc;
    private final TransactionEntityRepository repository;

    TransactionEndpointsIT(MockMvc mockMvc, TransactionEntityRepository repository) {
        this.mockMvc = mockMvc;
        this.repository = repository;
    }

    @Test
    void should_returnCreatedWithLocation_when_creatingTransaction() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}
                """;

        // when
        var response = mockMvc.perform(
                post(TRANSACTIONS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        var result = response.andExpect(status().isCreated())
                .andDo(document(
                        "create-transaction",
                        preprocessResponse(
                                replacePattern(UUID_PATTERN, "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f"),
                                replacePattern(TIMESTAMP_PATTERN, "2026-01-15T10:30:00.000Z"),
                                modifyHeaders().set(HttpHeaders.LOCATION, "http://localhost/api/v1/transactions/{id}")),
                        ResourceDocumentation.resource(ResourceSnippetParameters.builder()
                                .tag("Transações")
                                .summary("Cria uma transação")
                                .description("Registra uma transação financeira e devolve o recurso criado.")
                                .requestFields(
                                        fieldWithPath("description")
                                                .description("Descrição da transação, até 255 caracteres."),
                                        fieldWithPath("category").description("Categoria da transação."),
                                        fieldWithPath("amount")
                                                .description("Valor positivo, em texto, com duas casas decimais."),
                                        fieldWithPath("currency").description("Moeda; apenas `BRL`."))
                                .responseFields(
                                        fieldWithPath("id").description("Identificador da transação."),
                                        fieldWithPath("description").description("Descrição da transação."),
                                        fieldWithPath("category").description("Categoria da transação."),
                                        fieldWithPath("amount")
                                                .description("Valor, em texto, com duas casas decimais."),
                                        fieldWithPath("currency").description("Moeda da transação."),
                                        fieldWithPath("createdAt")
                                                .description("Data e hora do registro, em UTC (ISO 8601)."))
                                .responseHeaders(headerWithName(HttpHeaders.LOCATION)
                                        .description("URL absoluta da transação criada."))
                                .build())))
                .andReturn();
        var id = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(result.getRequest().getRequestURL() + "/" + id);
    }

    @Test
    void should_returnPageNewestFirst_when_listingWithoutParameters() throws Exception {
        // given
        saveAt("Mercado", "2026-03-01T10:00:00Z");
        saveAt("Padaria", "2026-03-03T10:00:00Z");
        saveAt("Feira", "2026-03-02T10:00:00Z");

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].description").value("Padaria"))
                .andExpect(jsonPath("$.content[1].description").value("Feira"))
                .andExpect(jsonPath("$.content[2].description").value("Mercado"))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andDo(document(
                        "list-transactions",
                        preprocessResponse(
                                replacePattern(UUID_PATTERN, "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f"),
                                replacePattern(TIMESTAMP_PATTERN, "2026-01-15T10:30:00.000Z")),
                        ResourceDocumentation.resource(ResourceSnippetParameters.builder()
                                .tag("Transações")
                                .summary("Lista as transações")
                                .description(
                                        "Lista as transações de forma paginada, da mais recente para a mais antiga.")
                                .queryParameters(
                                        parameterWithName("category")
                                                .description("Filtra pela categoria da transação.")
                                                .optional(),
                                        parameterWithName("page")
                                                .description("Número da página, a partir de 0; padrão 0.")
                                                .optional(),
                                        parameterWithName("size")
                                                .description("Itens por página, de 1 a 100; padrão 20.")
                                                .optional(),
                                        parameterWithName("sort")
                                                .description(
                                                        "Ordenação no formato `campo,direção`, com campo `createdAt` ou `amount` e direção `asc` ou `desc`; padrão `createdAt,desc`.")
                                                .optional())
                                .responseFields(
                                        fieldWithPath("content[]").description("Transações da página."),
                                        fieldWithPath("content[].id").description("Identificador da transação."),
                                        fieldWithPath("content[].description").description("Descrição da transação."),
                                        fieldWithPath("content[].category").description("Categoria da transação."),
                                        fieldWithPath("content[].amount")
                                                .description("Valor, em texto, com duas casas decimais."),
                                        fieldWithPath("content[].currency").description("Moeda da transação."),
                                        fieldWithPath("content[].createdAt")
                                                .description("Data e hora do registro, em UTC (ISO 8601)."),
                                        fieldWithPath("page").description("Metadados da paginação."),
                                        fieldWithPath("page.size").description("Itens por página aplicado."),
                                        fieldWithPath("page.number").description("Número da página, a partir de 0."),
                                        fieldWithPath("page.totalElements")
                                                .description("Total de transações que atendem ao filtro."),
                                        fieldWithPath("page.totalPages").description("Total de páginas."))
                                .build())));
    }

    @Test
    void should_returnTransaction_when_idExists() throws Exception {
        // given
        var id = UUID.randomUUID();
        repository.save(new TransactionEntity(
                id, "Mercado", new BigDecimal("80.00"), Category.GROCERIES, Instant.parse("2026-03-01T10:00:00Z")));

        // when
        var response = mockMvc.perform(RestDocumentationRequestBuilders.get(TRANSACTIONS_PATH + "/{id}", id));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.amount").value("80.00"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
                .andDo(document(
                        "get-transaction",
                        preprocessResponse(
                                replacePattern(UUID_PATTERN, "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f"),
                                replacePattern(TIMESTAMP_PATTERN, "2026-01-15T10:30:00.000Z")),
                        ResourceDocumentation.resource(ResourceSnippetParameters.builder()
                                .tag("Transações")
                                .summary("Consulta uma transação")
                                .description("Devolve a transação com o identificador informado.")
                                .pathParameters(parameterWithName("id").description("Identificador da transação."))
                                .responseFields(
                                        fieldWithPath("id").description("Identificador da transação."),
                                        fieldWithPath("description").description("Descrição da transação."),
                                        fieldWithPath("category").description("Categoria da transação."),
                                        fieldWithPath("amount")
                                                .description("Valor, em texto, com duas casas decimais."),
                                        fieldWithPath("currency").description("Moeda da transação."),
                                        fieldWithPath("createdAt")
                                                .description("Data e hora do registro, em UTC (ISO 8601)."))
                                .build())));
    }

    @ParameterizedTest
    @MethodSource("unknownIds")
    void should_returnNotFoundProblem_when_idIsUnknownOrMalformed(String id) throws Exception {
        // when
        var response = mockMvc.perform(RestDocumentationRequestBuilders.get(TRANSACTIONS_PATH + "/{id}", id));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:transaction-not-found"))
                .andExpect(jsonPath("$.title").value("Transação não encontrada"))
                .andExpect(jsonPath("$.detail").value("A transação solicitada não existe."))
                .andExpect(jsonPath("$.instance").value(TRANSACTIONS_PATH + "/" + id))
                .andDo(document(
                        "get-transaction-not-found",
                        preprocessResponse(
                                replacePattern(INSTANCE_PATTERN, "\"instance\":\"" + TRANSACTIONS_PATH + "/{id}\"")),
                        ResourceDocumentation.resource(ResourceSnippetParameters.builder()
                                .tag("Transações")
                                .summary("Consulta uma transação")
                                .pathParameters(parameterWithName("id").description("Identificador da transação."))
                                .responseFields(
                                        fieldWithPath("type").description("Tipo do problema."),
                                        fieldWithPath("title").description("Resumo do problema."),
                                        fieldWithPath("status").description("Status HTTP."),
                                        fieldWithPath("detail").description("Explicação do problema."),
                                        fieldWithPath("instance").description("Caminho da requisição."))
                                .build())));
    }

    static Stream<String> unknownIds() {
        return Stream.of(UUID.randomUUID().toString(), "abc");
    }

    private void saveAt(String description, String createdAt) {
        repository.save(new TransactionEntity(
                UUID.randomUUID(), description, new BigDecimal("10.00"), Category.GROCERIES, Instant.parse(createdAt)));
    }

    @Test
    void should_returnCreatedTransaction_when_listingByCategory() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}
                """;
        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("80.00"))
                .andExpect(jsonPath("$.currency").value("BRL"));

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param("category", "GROCERIES"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].description").value("Mercado"))
                .andExpect(jsonPath("$.content[0].category").value("GROCERIES"))
                .andExpect(jsonPath("$.content[0].amount").value("80.00"))
                .andExpect(jsonPath("$.content[0].currency").value("BRL"));
    }

    @Test
    void should_returnSameCreatedAtInUtc_when_creatingAndListing() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}
                """;
        var before = Instant.now();

        // when
        var created = mockMvc.perform(post(TRANSACTIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        var after = Instant.now();

        // then
        var createdAt = JsonPath.<String>read(created.getResponse().getContentAsString(), "$.createdAt");
        assertThat(createdAt).endsWith("Z");
        assertThat(Instant.parse(createdAt)).isBetween(before.truncatedTo(ChronoUnit.MILLIS), after);
        mockMvc.perform(get(TRANSACTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].createdAt").value(createdAt));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void should_returnBadRequestAndSaveNothing_when_amountOrCurrencyIsInvalid(String body) throws Exception {
        // when
        var response = mockMvc.perform(
                post(TRANSACTIONS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

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
                post(TRANSACTIONS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

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
