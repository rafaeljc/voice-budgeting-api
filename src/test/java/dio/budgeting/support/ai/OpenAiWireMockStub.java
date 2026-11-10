package dio.budgeting.support.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OpenAiWireMockStub implements AiStub {

    private static final String PAYLOADS = "ai/openai/";
    private static final String TRANSCRIPTION_PATH = "/v1/audio/transcriptions";
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String SPEECH_PATH = "/v1/audio/speech";

    private final WireMockServer server;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OpenAiWireMockStub(WireMockServer server) {
        this.server = server;
    }

    @Override
    public void givenTranscription(String text) {
        server.stubFor(post(urlEqualTo(TRANSCRIPTION_PATH))
                .willReturn(json(payload("transcription.json").replace("${text}", quoted(text)))));
    }

    @Override
    public String transcribedAudioFilename() {
        var requests = server.findAll(postRequestedFor(urlEqualTo(TRANSCRIPTION_PATH)));
        return requests.getLast().getParts().stream()
                .filter(part -> "file".equals(part.getName()))
                .findFirst()
                .orElseThrow()
                .getFileName();
    }

    @Override
    public void givenFailure(AiStage stage, AiFailure failure) {
        var response =
                switch (failure) {
                    case CONNECTION_FAILURE -> aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
                    case RATE_LIMITED -> aResponse().withStatus(429);
                    case PROVIDER_ERROR -> aResponse().withStatus(500);
                    case REQUEST_REJECTED -> aResponse().withStatus(401);
                };
        server.stubFor(post(urlEqualTo(path(stage))).willReturn(response));
    }

    @Override
    public void givenToolCall(String tool, String argumentsJson) {
        givenToolCalls(new AiToolCall(tool, argumentsJson));
    }

    // One chat response per command: the model asks for the tools and never gets a second turn
    @Override
    public void givenToolCalls(AiToolCall... calls) {
        var toolCalls = IntStream.range(0, calls.length)
                .mapToObj(index -> payload("tool-call.json")
                        .replace("${id}", quoted("call_" + (index + 1)))
                        .replace("${tool}", quoted(calls[index].tool()))
                        .replace("${arguments}", quoted(calls[index].argumentsJson())))
                .collect(Collectors.joining(",", "[", "]"));
        givenChatResponse(payload("chat-tool-call.json").replace("${toolCalls}", toolCalls));
    }

    @Override
    public void givenAnswer(String text) {
        givenChatResponse(payload("chat-answer.json").replace("${text}", quoted(text)));
    }

    private void givenChatResponse(String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(json(body)));
    }

    @Override
    public void givenSpeech(byte[] audio) {
        server.stubFor(post(urlEqualTo(SPEECH_PATH))
                .willReturn(aResponse().withHeader("Content-Type", "audio/mpeg").withBody(audio)));
    }

    @Override
    public String speechText() {
        var body = server.findAll(postRequestedFor(urlEqualTo(SPEECH_PATH)))
                .getLast()
                .getBodyAsString();
        return jsonMapper.readTree(body).path("input").asString();
    }

    @Override
    public String assistantCommand() {
        var body = server.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)))
                .getLast()
                .getBodyAsString();
        return StreamSupport.stream(jsonMapper.readTree(body).path("messages").spliterator(), false)
                .filter(message -> "user".equals(message.path("role").asString()))
                .map(message -> messageText(message.path("content")))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Override
    public int assistantCallCount() {
        return server.findAll(postRequestedFor(urlEqualTo(CHAT_PATH))).size();
    }

    @Override
    public void reset() {
        server.resetAll();
    }

    // The provider sends the content either as a string or as a list of typed parts
    private static String messageText(JsonNode content) {
        if (content.isString()) {
            return content.asString();
        }
        return StreamSupport.stream(content.spliterator(), false)
                .map(part -> part.path("text").asString())
                .collect(Collectors.joining());
    }

    private static String path(AiStage stage) {
        return switch (stage) {
            case TRANSCRIPTION -> TRANSCRIPTION_PATH;
            case ASSISTANT -> CHAT_PATH;
            case SPEECH -> SPEECH_PATH;
        };
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
