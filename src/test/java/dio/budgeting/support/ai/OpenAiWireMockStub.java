package dio.budgeting.support.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

public class OpenAiWireMockStub implements AiStub {

    private static final String PAYLOADS = "ai/openai/";
    private static final String CHAT_SCENARIO = "chat";
    private static final String TOOL_CALLED = "tool-called";

    private final WireMockServer server;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private boolean toolCallGiven;

    public OpenAiWireMockStub(WireMockServer server) {
        this.server = server;
    }

    @Override
    public void givenTranscription(String text) {
        server.stubFor(post(urlEqualTo("/v1/audio/transcriptions"))
                .willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody(text)));
    }

    @Override
    public void givenTranscriptionFailure(AiFailure failure) {
        var response =
                switch (failure) {
                    case CONNECTION_FAILURE -> aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
                    case RATE_LIMITED -> aResponse().withStatus(429);
                    case PROVIDER_ERROR -> aResponse().withStatus(500);
                    case REQUEST_REJECTED -> aResponse().withStatus(401);
                };
        server.stubFor(post(urlEqualTo("/v1/audio/transcriptions")).willReturn(response));
    }

    // The first chat call asks for the tool; the second one carries the tool result and gets the final answer
    @Override
    public void givenToolCall(String tool, String argumentsJson) {
        var body = payload("chat-tool-call.json")
                .replace("${tool}", quoted(tool))
                .replace("${arguments}", quoted(argumentsJson));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario(CHAT_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(TOOL_CALLED)
                .willReturn(json(body)));
        toolCallGiven = true;
    }

    @Override
    public void givenAnswer(String text) {
        var body = payload("chat-answer.json").replace("${text}", quoted(text));
        var stub = post(urlEqualTo("/v1/chat/completions")).inScenario(CHAT_SCENARIO);
        server.stubFor((toolCallGiven ? stub.whenScenarioStateIs(TOOL_CALLED) : stub).willReturn(json(body)));
    }

    @Override
    public void givenSpeech(byte[] audio) {
        server.stubFor(post(urlEqualTo("/v1/audio/speech"))
                .willReturn(aResponse().withHeader("Content-Type", "audio/mpeg").withBody(audio)));
    }

    @Override
    public void reset() {
        server.resetAll();
        toolCallGiven = false;
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }

    private String quoted(String value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static String payload(String name) {
        try {
            return new ClassPathResource(PAYLOADS + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
