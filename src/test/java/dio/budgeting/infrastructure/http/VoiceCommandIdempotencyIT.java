package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import dio.budgeting.infrastructure.persistence.repository.TransactionProposalEntityRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiFailure;
import dio.budgeting.support.ai.AiStage;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.security.AccessTokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

// The two sends must overlap, so the calls go through a real server instead of MockMvc
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureAiStub
class VoiceCommandIdempotencyIT {
    private static final Duration SPEECH_DELAY = Duration.ofSeconds(2);

    @LocalServerPort
    private int port;

    private final AiStub aiStub;
    private final TransactionProposalEntityRepository proposalRepository;

    VoiceCommandIdempotencyIT(AiStub aiStub, TransactionProposalEntityRepository proposalRepository) {
        this.aiStub = aiStub;
        this.proposalRepository = proposalRepository;
    }

    @Test
    void should_answerImmediatelyWithConflict_when_sameKeyAndAudioAreSentConcurrently() throws Exception {
        // given
        givenExpenseCommand();
        aiStub.givenDelayedSpeech(new byte[] {1, 2, 3, 4}, SPEECH_DELAY);
        var key = UUID.randomUUID();

        // when
        var results = callTogether(List.of(() -> send(key), () -> send(key)));

        // then
        var statuses = results.stream().map(result -> result.status()).toList();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        var ok = results.stream()
                .filter(result -> result.status() == 200)
                .findFirst()
                .orElseThrow();
        var conflict = results.stream()
                .filter(result -> result.status() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(conflict.finishedAt()).isBefore(ok.finishedAt());
        assertThat(aiStub.assistantCallCount()).isEqualTo(1);
        assertThat(proposalRepository.count()).isEqualTo(1);
    }

    @Test
    void should_acceptSameKeyAgain_when_firstSendFailed() throws Exception {
        // given
        var key = UUID.randomUUID();
        aiStub.givenFailure(AiStage.TRANSCRIPTION, AiFailure.PROVIDER_ERROR);
        assertThat(send(key).status()).isEqualTo(503);
        aiStub.reset();
        givenExpenseCommand();
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});

        // when
        var retry = send(key);

        // then
        assertThat(retry.status()).isEqualTo(200);
        assertThat(proposalRepository.count()).isEqualTo(1);
    }

    private void givenExpenseCommand() {
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
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

    private Result send(UUID key) throws IOException {
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
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange((request, response) ->
                        new Result(response.getStatusCode().value(), Instant.now()));
    }

    private record Result(int status, Instant finishedAt) {}
}
