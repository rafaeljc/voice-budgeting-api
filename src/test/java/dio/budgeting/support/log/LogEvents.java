package dio.budgeting.support.log;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.test.system.CapturedOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class LogEvents {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private LogEvents() {}

    public static List<JsonNode> from(CapturedOutput output) {
        return Arrays.stream(output.getAll().split("\n"))
                .filter(line -> line.startsWith("{"))
                .map(JSON_MAPPER::readTree)
                .toList();
    }

    public static JsonNode withMessage(CapturedOutput output, String message) {
        return from(output).stream()
                .filter(event -> message.equals(event.path("message").asString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log event with message: " + message));
    }
}
