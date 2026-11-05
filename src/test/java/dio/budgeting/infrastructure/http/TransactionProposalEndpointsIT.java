package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static dio.budgeting.support.ApiPaths.TRANSACTION_PROPOSALS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.infrastructure.persistence.repository.TransactionProposalEntityRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.log.LogEvents;
import dio.budgeting.support.security.AccessTokens;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void should_logConfirmationWithIdsOnlyAndTraceId_when_confirming(CapturedOutput output) throws Exception {
        // given
        var proposalId = saveProposal();

        // when
        var response = confirm(proposalId).andReturn().getResponse();

        // then
        var event = LogEvents.withMessage(output, "Transaction proposal confirmed");
        assertThat(event.path("transactionProposalId").asString()).isEqualTo(proposalId.toString());
        assertThat(event.path("transactionId").asString())
                .isEqualTo(JsonPath.<String>read(response.getContentAsString(), "$.id"));
        assertThat(event.path("traceId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(output.getAll()).doesNotContain("Mercado");
    }

    @Test
    void should_logCancelationWithIdOnlyAndTraceId_when_canceling(CapturedOutput output) throws Exception {
        // given
        var proposalId = saveProposal();

        // when
        var response = cancel(proposalId).andReturn().getResponse();

        // then
        var event = LogEvents.withMessage(output, "Transaction proposal canceled");
        assertThat(event.path("transactionProposalId").asString()).isEqualTo(proposalId.toString());
        assertThat(event.path("traceId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(output.getAll()).doesNotContain("Mercado");
    }

    @ParameterizedTest
    @MethodSource("decisions")
    void should_returnNotFoundProblem_when_proposalIdIsUnknownOrMalformed(String action, String id) throws Exception {
        // when
        var response = mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/" + action, id)
                .header(AUTHORIZATION, AccessTokens.authorization()));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:transaction-proposal-not-found"))
                .andExpect(jsonPath("$.title").value("Proposta de transação não encontrada"))
                .andExpect(
                        jsonPath("$.detail").value("A proposta de transação não existe, já foi decidida ou expirou."))
                .andExpect(jsonPath("$.instance").value(TRANSACTION_PROPOSALS_PATH + "/" + id + "/" + action));
    }

    @ParameterizedTest
    @MethodSource("decisions")
    void should_returnSameNotFoundProblemAndKeepProposal_when_proposalBelongsToAnotherUser(
            String action, String ignored) throws Exception {
        // given
        var proposalId = saveProposal();
        var otherUser = new UserId(UUID.randomUUID());

        // when
        var response = mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/" + action, proposalId)
                .header(AUTHORIZATION, AccessTokens.authorization(otherUser)));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:transaction-proposal-not-found"))
                .andExpect(
                        jsonPath("$.detail").value("A proposta de transação não existe, já foi decidida ou expirou."));
        assertThat(proposalRepository.findById(proposalId)).isPresent();
        assertThat(transactionRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "cancel"})
    void should_returnNotFoundAndKeepProposal_when_proposalIsExpired(String action) throws Exception {
        // given
        var proposalId = saveProposal(Instant.now().minusSeconds(1));

        // when
        var response = mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/" + action, proposalId)
                .header(AUTHORIZATION, AccessTokens.authorization()));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:transaction-proposal-not-found"))
                .andExpect(
                        jsonPath("$.detail").value("A proposta de transação não existe, já foi decidida ou expirou."));
        assertThat(proposalRepository.findById(proposalId)).isPresent();
        assertThat(transactionRepository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("decisions")
    void should_returnUnauthorized_when_tokenIsMissing(String action, String id) throws Exception {
        // when
        var response = mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/" + action, id));

        // then
        response.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:unauthenticated"));
    }

    static Stream<Arguments> decisions() {
        var unknown = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("confirm", unknown),
                Arguments.of("confirm", "abc"),
                Arguments.of("cancel", unknown),
                Arguments.of("cancel", "abc"));
    }

    private UUID saveProposal() {
        return saveProposal(Instant.now().plus(Duration.ofMinutes(5)));
    }

    private UUID saveProposal(Instant expiresAt) {
        var id = UUID.randomUUID();
        proposalRepository.save(new TransactionProposalEntity(
                id, USER_ID, "Mercado", new BigDecimal("80.00"), Category.GROCERIES, PROPOSED_AT, expiresAt));
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
