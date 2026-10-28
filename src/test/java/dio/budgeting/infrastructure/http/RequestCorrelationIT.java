package dio.budgeting.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.log.LogEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestCorrelationIT {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    private final MockMvc mockMvc;

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
        var event = LogEvents.withMessage(output, "Transactions listed");
        assertThat(event.path("traceId").asString()).isEqualTo(requestId);
        assertThat(event.path("resultCount").asInt()).isZero();
    }

    @Test
    void should_continueReceivedTrace_when_traceparentIsSent() throws Exception {
        // given
        var traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        // when
        var response = mockMvc.perform(
                        get("/transactions/GROCERIES").header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01"))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(traceId);
    }

    @Test
    void should_ignoreClientRequestId_when_headerIsSent() throws Exception {
        // when
        var response = mockMvc.perform(get("/transactions/GROCERIES").header("X-Request-Id", "client-chosen-id"))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getHeader("X-Request-Id")).matches(TRACE_ID_PATTERN);
    }
}
