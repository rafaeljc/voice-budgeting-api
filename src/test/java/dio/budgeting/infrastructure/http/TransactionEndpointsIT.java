package dio.budgeting.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.support.IntegrationTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    void should_returnCreatedTransaction_when_listingByCategory() throws Exception {
        // given
        var body = """
                {"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}
                """;
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
