package dio.budgeting.infrastructure.ai;

import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import dio.budgeting.application.TextToSpeechGateway;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.resilience.EnableResilience;
import dio.budgeting.support.security.AccessTokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The overlapping sends need a real server instead of MockMvc; the stages with one permit make the overlap a refusal
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureAiStub
@EnableResilience
@TestPropertySource(
        properties = {
            "resilience4j.bulkhead.instances.speech-to-text.max-concurrent-calls=1",
            "resilience4j.bulkhead.instances.text-to-speech.max-concurrent-calls=1"
        })
class AiBulkheadIT {
    private static final Duration TRANSCRIPTION_DELAY = Duration.ofSeconds(2);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    private final AiStub aiStub;
    private final TextToSpeechGateway textToSpeechGateway;

    AiBulkheadIT(AiStub aiStub, TextToSpeechGateway textToSpeechGateway) {
        this.aiStub = aiStub;
        this.textToSpeechGateway = textToSpeechGateway;
    }

    @Test
    void should_rejectWithServiceUnavailable_when_stageIsFull() throws Exception {
        // given
        givenSlowTranscriptionStage();

        // when
        var results = callTogether(List.of(this::send, this::send));

        // then
        var statuses = results.stream().map(result -> result.status()).toList();
        assertThat(statuses).containsExactlyInAnyOrder(200, 503);
        var refused = results.stream()
                .filter(result -> result.status() == 503)
                .findFirst()
                .orElseThrow();
        assertThat(refused.retryAfter()).isEqualTo("30");
        assertThat(refused.body().path("type").asString())
                .isEqualTo("urn:problem-type:budgeting:assistant-unavailable");
    }

    @Test
    void should_notHoldSpeechPermit_when_waitingForCachedSpeech() throws Exception {
        // given
        var audio = new byte[] {1, 2, 3, 4};
        aiStub.givenDelayedSpeech(audio, Duration.ofSeconds(1));
        var start = new CountDownLatch(1);

        // when
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<byte[]>>();
            for (var i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return textToSpeechGateway.synthesize("Registrar mercado de 80 reais?");
                }));
            }
            start.countDown();

            // then
            for (var future : futures) {
                assertThat(future.get()).isEqualTo(audio);
            }
        }
        assertThat(aiStub.speechCallCount()).isEqualTo(1);
    }

    private void givenSlowTranscriptionStage() {
        aiStub.givenDelayedTranscription("gastei 80 reais no mercado", TRANSCRIPTION_DELAY);
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});
    }

    private List<Result> callTogether(List<Callable<Result>> calls) throws Exception {
        var ready = new CountDownLatch(calls.size());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream()
                    .map(call -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return call.call();
                    }))
                    .toList();
            ready.await();
            start.countDown();
            var results = new ArrayList<Result>();
            for (var future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private Result send() throws IOException {
        var parts = new LinkedMultiValueMap<String, Object>();
        var content = Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a"));
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "recording-1.m4a";
            }
        });
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri(VOICE_COMMAND_PATH)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange((request, response) -> {
                    var body = response.getBody().readAllBytes();
                    // The success answer is audio; only the refusal is a Problem Details JSON
                    var problem = response.getStatusCode().isError() ? JSON_MAPPER.readTree(body) : null;
                    return new Result(
                            response.getStatusCode().value(),
                            response.getHeaders().getFirst("Retry-After"),
                            problem);
                });
    }

    private record Result(int status, String retryAfter, JsonNode body) {}
}
