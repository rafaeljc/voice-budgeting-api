package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AiFailure;
import dio.budgeting.support.ai.AiStage;
import dio.budgeting.support.ai.AiStub;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.resilience.EnableResilience;
import dio.budgeting.support.security.AccessTokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureAiStub
@EnableResilience
class VoiceCommandCircuitBreakerIT {
    // The circuit needs 5 calls before it can open
    private static final int MINIMUM_CALLS = 5;

    private final MockMvc mockMvc;
    private final AiStub aiStub;

    VoiceCommandCircuitBreakerIT(MockMvc mockMvc, AiStub aiStub) {
        this.mockMvc = mockMvc;
        this.aiStub = aiStub;
    }

    @Test
    void should_rejectWithoutCallingAi_when_circuitIsOpen() throws Exception {
        // given
        aiStub.givenFailure(AiStage.TRANSCRIPTION, AiFailure.RATE_LIMITED);
        for (var i = 0; i < MINIMUM_CALLS; i++) {
            mockMvc.perform(voiceCommand()).andExpect(status().isServiceUnavailable());
        }
        var callsBefore = aiStub.transcriptionCallCount();
        givenHealthyAi();

        // when
        var response = mockMvc.perform(voiceCommand());

        // then
        response.andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:assistant-unavailable"));
        assertThat(aiStub.transcriptionCallCount()).isEqualTo(callsBefore);
    }

    private void givenHealthyAi() {
        aiStub.givenTranscription("gastei 80 reais no mercado");
        aiStub.givenToolCall("propose-transaction", """
                {"arguments": {"description": "Mercado", "amount": 80.00, "category": "GROCERIES"}}
                """);
        aiStub.givenSpeech(new byte[] {1, 2, 3, 4});
    }

    private static MockMultipartHttpServletRequestBuilder voiceCommand() throws IOException {
        return multipart(VOICE_COMMAND_PATH)
                .file(new MockMultipartFile(
                        "file",
                        "recording-1.m4a",
                        "audio/mp4",
                        Files.readAllBytes(Path.of("src/test/resources/audio/recording-1.m4a"))))
                .header(AUTHORIZATION, AccessTokens.authorization());
    }
}
