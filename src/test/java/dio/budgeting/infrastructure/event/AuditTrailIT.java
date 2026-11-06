package dio.budgeting.infrastructure.event;

import static dio.budgeting.support.ApiPaths.TRANSACTION_PROPOSALS_PATH;
import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.UserId;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.security.AccessTokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureAiStub
class AuditTrailIT {
    private static final String COLLECTION = "audit_entries";
    private static final String COMMAND = "gastei 80 reais no mercado";

    private final MockMvc mockMvc;
    private final AiStub aiStub;
    private final MongoTemplate mongoTemplate;

    AuditTrailIT(MockMvc mockMvc, AiStub aiStub, MongoTemplate mongoTemplate) {
        this.mockMvc = mockMvc;
        this.aiStub = aiStub;
        this.mongoTemplate = mongoTemplate;
    }

    @Test
    void should_appendCreatedEntryWithAllFields_when_commandRegistersExpense() throws Exception {
        // given
        var user = newUser();

        // when
        var result = registerExpense(user);

        // then
        var requestId = result.getResponse().getHeader("X-Request-Id");
        var proposalId = proposalId(result);
        var entry = awaitEntries(user, 1).getFirst();
        assertThat(entry.getString("type")).isEqualTo("TRANSACTION_PROPOSAL_CREATED");
        assertThat(entry.get("userId", UUID.class)).isEqualTo(user.uuid());
        assertThat(entry.get("occurredAt", Date.class)).isNotNull();
        assertThat(entry.getString("traceId")).isEqualTo(requestId);
        assertThat(entry.get("transactionProposalId", UUID.class)).isEqualTo(proposalId);
        assertThat(entry.containsKey("transactionId")).isFalse();
        assertThat(entry.get("audio", Document.class))
                .containsEntry("format", "M4A")
                .containsEntry("sizeBytes", (long) recording().getSize());
        assertThat(entry.getString("transcription")).isEqualTo(COMMAND);
        var proposal = entry.get("proposal", Document.class);
        assertThat(proposal.getString("description")).isEqualTo("Mercado");
        assertThat(proposal.get("amount")).isInstanceOf(Decimal128.class);
        assertThat(proposal.get("amount", Decimal128.class).bigDecimalValue()).isEqualByComparingTo("80.00");
        assertThat(proposal.getString("currency")).isEqualTo("BRL");
        assertThat(proposal.getString("category")).isEqualTo("GROCERIES");
        assertThat(proposal.get("expiresAt", Date.class)).isNotNull();
    }

    @Test
    void should_appendNotUnderstoodEntry_when_assistantDoesNotUnderstand() throws Exception {
        // given
        var user = newUser();
        aiStub.givenTranscription("me conte uma piada");
        aiStub.givenAnswer("Claro!");
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        sendVoiceCommand(user);

        // then
        var entry = awaitEntries(user, 1).getFirst();
        assertThat(entry.getString("type")).isEqualTo("VOICE_COMMAND_NOT_UNDERSTOOD");
        assertThat(entry.getString("transcription")).isEqualTo("me conte uma piada");
        assertThat(entry.get("audio", Document.class)).containsEntry("format", "M4A");
        assertThat(entry.containsKey("proposal")).isFalse();
        assertThat(entry.containsKey("transactionProposalId")).isFalse();
    }

    @Test
    void should_appendConfirmedEntryWithTransactionId_when_confirming() throws Exception {
        // given
        var user = newUser();
        var proposalId = proposalId(registerExpense(user));

        // when
        var result = mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/confirm", proposalId)
                        .header(AUTHORIZATION, AccessTokens.authorization(user)))
                .andExpect(status().isCreated())
                .andReturn();

        // then
        var transactionId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        var entry = awaitEntries(user, 2).get(1);
        assertThat(entry.getString("type")).isEqualTo("TRANSACTION_PROPOSAL_CONFIRMED");
        assertThat(entry.get("transactionProposalId", UUID.class)).isEqualTo(proposalId);
        assertThat(entry.get("transactionId", UUID.class)).isEqualTo(transactionId);
        assertThat(entry.getString("traceId")).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(entry.containsKey("proposal")).isFalse();
    }

    @Test
    void should_appendCanceledEntry_when_canceling() throws Exception {
        // given
        var user = newUser();
        var proposalId = proposalId(registerExpense(user));

        // when
        mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/cancel", proposalId)
                        .header(AUTHORIZATION, AccessTokens.authorization(user)))
                .andExpect(status().isNoContent());

        // then
        var entry = awaitEntries(user, 2).get(1);
        assertThat(entry.getString("type")).isEqualTo("TRANSACTION_PROPOSAL_CANCELED");
        assertThat(entry.get("transactionProposalId", UUID.class)).isEqualTo(proposalId);
        assertThat(entry.containsKey("transactionId")).isFalse();
    }

    @Test
    void should_appendNothing_when_commandIsAQueryOrDecisionIsNotFound() throws Exception {
        // given
        var user = newUser();
        aiStub.givenTranscription("quanto gastei com mercado");
        aiStub.givenToolCall("summarize-transactions-by-category", """
                {"category": "GROCERIES"}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        sendVoiceCommand(user);
        mockMvc.perform(post(TRANSACTION_PROPOSALS_PATH + "/{id}/cancel", UUID.randomUUID())
                        .header(AUTHORIZATION, AccessTokens.authorization(user)))
                .andExpect(status().isNotFound());
        registerExpense(user);

        // then: the later entry of the same user is there, so the earlier ones would have arrived too
        var entries = awaitEntries(user, 1);
        assertThat(entries)
                .extracting(entry -> entry.getString("type"))
                .containsExactly("TRANSACTION_PROPOSAL_CREATED");
    }

    private MvcResult registerExpense(UserId user) throws Exception {
        aiStub.givenTranscription(COMMAND);
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});
        return sendVoiceCommand(user);
    }

    private MvcResult sendVoiceCommand(UserId user) throws Exception {
        return mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .file(recording())
                        .header(AUTHORIZATION, AccessTokens.authorization(user)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static UUID proposalId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.transactionProposal.id"));
    }

    // Each test uses its own user: an async write from a previous test can land after the cleanup
    private static UserId newUser() {
        return new UserId(UUID.randomUUID());
    }

    private List<Document> awaitEntries(UserId user, int expected) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(entriesOf(user)).hasSize(expected));
        return entriesOf(user);
    }

    private List<Document> entriesOf(UserId user) {
        return mongoTemplate.find(query(where("userId").is(user.uuid())), Document.class, COLLECTION).stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.get("occurredAt", Date.class)))
                .toList();
    }

    private static MockMultipartFile recording() throws IOException {
        return new MockMultipartFile(
                "file",
                "recording-1.m4a",
                "audio/mp4",
                Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a")));
    }
}
