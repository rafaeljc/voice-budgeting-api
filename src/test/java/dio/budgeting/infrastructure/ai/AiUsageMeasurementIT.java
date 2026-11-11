package dio.budgeting.infrastructure.ai;

import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureAiStub
class AiUsageMeasurementIT {
    private static final String COLLECTION = "ai_usage_entries";

    private final MockMvc mockMvc;
    private final AiStub aiStub;
    private final MongoTemplate mongoTemplate;

    AiUsageMeasurementIT(MockMvc mockMvc, AiStub aiStub, MongoTemplate mongoTemplate) {
        this.mockMvc = mockMvc;
        this.aiStub = aiStub;
        this.mongoTemplate = mongoTemplate;
    }

    @Test
    void should_recordOneEntryPerModelResponse_when_commandCompletes() throws Exception {
        // given
        var user = newUser();
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        var result = sendVoiceCommand(user);

        // then
        var requestId = result.getResponse().getHeader("X-Request-Id");
        var entries = awaitEntries(user, 3);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.get("userId", UUID.class)).isEqualTo(user.uuid());
            assertThat(entry.getString("traceId")).isEqualTo(requestId);
            assertThat(entry.get("occurredAt", Date.class)).isNotNull();
        });
        var transcription = entry(entries, "TRANSCRIPTION");
        assertThat(transcription.getString("model")).isEqualTo("whisper-1");
        assertThat(transcription.getDouble("transcribedAudioSeconds")).isEqualTo(3.5);
        var assistant = entry(entries, "ASSISTANT");
        assertThat(assistant.getString("model")).isEqualTo("gpt-4o-mini");
        assertThat(assistant.get("inputTokens", Number.class).longValue()).isEqualTo(120);
        assertThat(assistant.get("cachedInputTokens", Number.class).longValue()).isEqualTo(64);
        assertThat(assistant.get("outputTokens", Number.class).longValue()).isEqualTo(30);
        var speech = entry(entries, "SPEECH");
        assertThat(speech.getString("model")).isEqualTo("gpt-4o-mini-tts");
        assertThat(speech.get("synthesizedCharacters", Number.class).intValue())
                .isEqualTo(aiStub.speechText().length());
    }

    @Test
    void should_recordAssistantEntry_when_toolArgumentsAreInvalid() throws Exception {
        // given
        var user = newUser();
        aiStub.givenTranscription("gastei menos 10 no mercado");
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": -10, "category": "GROCERIES"}}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .file(recording())
                        .header(AUTHORIZATION, AccessTokens.authorization(user)))
                .andExpect(status().isUnprocessableContent());

        // then: the chat response is measured before the tool throws, and the synthesis never happens
        var entries = awaitEntries(user, 2);
        assertThat(entries)
                .extracting(entry -> entry.getString("stage"))
                .containsExactlyInAnyOrder("TRANSCRIPTION", "ASSISTANT");
        assertThat(entry(entries, "ASSISTANT").get("inputTokens", Number.class).longValue())
                .isEqualTo(120);
    }

    @Test
    void should_notMeasureSpeech_when_speechComesFromCache() throws Exception {
        // given
        var user = newUser();
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        sendVoiceCommand(user);
        sendVoiceCommand(user);

        // then: the second command's ASSISTANT entry arrives, so a SPEECH entry for it would have too
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(entriesOf(user))
                .filteredOn(entry -> "ASSISTANT".equals(entry.getString("stage")))
                .hasSize(2));
        assertThat(entriesOf(user))
                .filteredOn(entry -> "SPEECH".equals(entry.getString("stage")))
                .hasSize(1);
        assertThat(aiStub.speechCallCount()).isEqualTo(1);
    }

    private MvcResult sendVoiceCommand(UserId user) throws Exception {
        return mockMvc.perform(multipart(VOICE_COMMAND_PATH)
                        .file(recording())
                        .header(AUTHORIZATION, AccessTokens.authorization(user)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Document entry(List<Document> entries, String stage) {
        return entries.stream()
                .filter(entry -> stage.equals(entry.getString("stage")))
                .findFirst()
                .orElseThrow();
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
        return mongoTemplate.find(query(where("userId").is(user.uuid())), Document.class, COLLECTION);
    }

    private static MockMultipartFile recording() throws IOException {
        return new MockMultipartFile(
                "file",
                "recording-1.m4a",
                "audio/mp4",
                Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a")));
    }
}
