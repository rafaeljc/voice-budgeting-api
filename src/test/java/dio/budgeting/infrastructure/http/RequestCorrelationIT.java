package dio.budgeting.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dio.budgeting.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestCorrelationIT {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    RequestCorrelationIT(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_returnTraceIdAsRequestIdAndLogItInJson_when_requestIsMade(CapturedOutput output) throws Exception {
        // when
        var response =
                mockMvc.perform(get("/transactions/GROCERIES")).andReturn().getResponse();

        // then
        var requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).matches(TRACE_ID_PATTERN);
        var event = logEvents(output).stream()
                .filter(line ->
                        "Transactions listed".equals(line.path("message").asString()))
                .findFirst()
                .orElseThrow();
        assertThat(event.path("traceId").asString()).isEqualTo(requestId);
        assertThat(event.path("resultCount").asInt()).isZero();
    }

    private List<JsonNode> logEvents(CapturedOutput output) {
        return Arrays.stream(output.getAll().split("\n"))
                .filter(line -> line.startsWith("{"))
                .map(jsonMapper::readTree)
                .toList();
    }
}
