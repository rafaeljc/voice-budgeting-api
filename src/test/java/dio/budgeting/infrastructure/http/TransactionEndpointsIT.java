package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class TransactionEndpointsIT {

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
        var result = response.andExpect(status().isCreated()).andReturn();
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
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void should_returnTransaction_when_idExists() throws Exception {
        // given
        var id = UUID.randomUUID();
        repository.save(new TransactionEntity(
                id, "Mercado", new BigDecimal("80.00"), Category.GROCERIES, Instant.parse("2026-03-01T10:00:00Z")));

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH + "/{id}", id));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.amount").value("80.00"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"));
    }

    @ParameterizedTest
    @MethodSource("unknownIds")
    void should_returnNotFoundProblem_when_idIsUnknownOrMalformed(String id) throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH + "/{id}", id));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:transaction-not-found"))
                .andExpect(jsonPath("$.title").value("Transação não encontrada"))
                .andExpect(jsonPath("$.detail").value("A transação solicitada não existe."))
                .andExpect(jsonPath("$.instance").value(TRANSACTIONS_PATH + "/" + id));
    }

    static Stream<String> unknownIds() {
        return Stream.of(UUID.randomUUID().toString(), "abc");
    }

    @Test
    void should_returnOnlyThatCategory_when_filteringByCategory() throws Exception {
        // given
        save("Mercado", Category.GROCERIES, "10.00", "2026-03-01T10:00:00Z");
        save("Feira", Category.GROCERIES, "10.00", "2026-03-02T10:00:00Z");
        save("Remédio", Category.PHARMA, "10.00", "2026-03-03T10:00:00Z");

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param("category", "GROCERIES"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].category").value(everyItem(is("GROCERIES"))))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void should_returnSecondPage_when_pageAndSizeAreGiven() throws Exception {
        // given
        for (var day = 1; day <= 5; day++) {
            save("Compra " + day, Category.GROCERIES, "10.00", "2026-03-0" + day + "T10:00:00Z");
        }

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param("page", "1").param("size", "2"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].description").value("Compra 3"))
                .andExpect(jsonPath("$.content[1].description").value("Compra 2"))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @ParameterizedTest
    @MethodSource("sortings")
    void should_orderAsRequested_when_sortIsGiven(String sort, List<String> expectedOrder) throws Exception {
        // given
        save("A", Category.GROCERIES, "30.00", "2026-03-01T10:00:00Z");
        save("B", Category.GROCERIES, "10.00", "2026-03-02T10:00:00Z");
        save("C", Category.GROCERIES, "20.00", "2026-03-03T10:00:00Z");

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param("sort", sort));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].description").value(contains(expectedOrder.toArray())));
    }

    static Stream<Arguments> sortings() {
        return Stream.of(
                Arguments.of("amount,asc", List.of("B", "C", "A")),
                Arguments.of("amount,desc", List.of("A", "C", "B")),
                Arguments.of("createdAt,asc", List.of("A", "B", "C")));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"createdAt,desc|SECOND|FIRST", "createdAt,asc|FIRST|SECOND"})
    void should_breakTiesById_when_createdAtIsEqual(String sort, String expectedFirst, String expectedSecond)
            throws Exception {
        // given
        var sameInstant = "2026-03-01T10:00:00Z";
        save(UUID.fromString("00000000-0000-4000-8000-000000000001"), "FIRST", "10.00", sameInstant);
        save(UUID.fromString("00000000-0000-4000-8000-000000000002"), "SECOND", "10.00", sameInstant);

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param("sort", sort));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value(expectedFirst))
                .andExpect(jsonPath("$.content[1].description").value(expectedSecond));
    }

    @ParameterizedTest
    @CsvSource({"size, 500, size, 100", "size, 0, size, 20", "page, -1, number, 0"})
    void should_adjustOutOfRangeValues_when_pageOrSizeIsOutOfRange(
            String parameter, String value, String metadata, int applied) throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).param(parameter, value));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.page." + metadata).value(applied));
    }

    private void save(String description, Category category, String amount, String createdAt) {
        repository.save(new TransactionEntity(
                UUID.randomUUID(), description, new BigDecimal(amount), category, Instant.parse(createdAt)));
    }

    private void save(UUID id, String description, String amount, String createdAt) {
        repository.save(new TransactionEntity(
                id, description, new BigDecimal(amount), Category.GROCERIES, Instant.parse(createdAt)));
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
