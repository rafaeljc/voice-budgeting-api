package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static dio.budgeting.support.ApiPaths.TRANSACTION_PROPOSALS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.infrastructure.persistence.repository.TransactionProposalEntityRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.security.AccessTokens;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
class TransactionProposalEndpointsIT {
    private static final UUID USER_ID = AccessTokens.DEFAULT_USER.uuid();
    private static final Instant PROPOSED_AT = Instant.parse("2026-03-01T10:00:00Z");

    private final MockMvc mockMvc;
    private final TransactionProposalEntityRepository proposalRepository;
    private final TransactionEntityRepository transactionRepository;

    TransactionProposalEndpointsIT(
            MockMvc mockMvc,
            TransactionProposalEntityRepository proposalRepository,
            TransactionEntityRepository transactionRepository) {
        this.mockMvc = mockMvc;
        this.proposalRepository = proposalRepository;
        this.transactionRepository = transactionRepository;
    }

    @Test
    void should_returnCreatedTransactionAndDeleteProposal_when_confirming() throws Exception {
        // given
        var proposalId = saveProposal();

        // when
        var response = mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/confirm", proposalId)
                .header(AUTHORIZATION, AccessTokens.authorization()));

        // then
        var result = response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.amount").value("80.00"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andReturn();
        var body = result.getResponse().getContentAsString();
        var transactionId = JsonPath.<String>read(body, "$.id");
        var createdAt = Instant.parse(JsonPath.<String>read(body, "$.createdAt"));
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost" + TRANSACTIONS_PATH + "/" + transactionId);
        assertThat(createdAt).isAfter(PROPOSED_AT);
        assertThat(transactionRepository.findById(UUID.fromString(transactionId)))
                .hasValueSatisfying(saved -> assertThat(saved.getUserId()).isEqualTo(USER_ID));
        assertThat(proposalRepository.findById(proposalId)).isEmpty();
    }

    @Test
    void should_returnNotFound_when_confirmingAgain() throws Exception {
        // given
        var proposalId = saveProposal();
        confirm(proposalId);

        // when
        var response = confirm(proposalId);

        // then
        response.andExpect(status().isNotFound());
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void should_returnNoContentAndDeleteProposal_when_canceling() throws Exception {
        // given
        var proposalId = saveProposal();

        // when
        var response = cancel(proposalId);

        // then
        response.andExpect(status().isNoContent());
        assertThat(proposalRepository.findById(proposalId)).isEmpty();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void should_returnNotFound_when_cancelingAgain() throws Exception {
        // given
        var proposalId = saveProposal();
        cancel(proposalId);

        // when
        var response = cancel(proposalId);

        // then
        response.andExpect(status().isNotFound());
    }

    private UUID saveProposal() {
        var id = UUID.randomUUID();
        proposalRepository.save(new TransactionProposalEntity(
                id, USER_ID, "Mercado", new BigDecimal("80.00"), Category.GROCERIES, PROPOSED_AT));
        return id;
    }

    private ResultActions confirm(UUID id) throws Exception {
        return mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/confirm", id)
                .header(AUTHORIZATION, AccessTokens.authorization()));
    }

    private ResultActions cancel(UUID id) throws Exception {
        return mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/cancel", id)
                .header(AUTHORIZATION, AccessTokens.authorization()));
    }
}
