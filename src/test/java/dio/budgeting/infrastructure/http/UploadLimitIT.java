package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.VOICE_COMMAND_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.support.IntegrationTest;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

// The MockMvc multipart resolver never enforces the upload limit, so this test needs a real server
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UploadLimitIT {

    private static final String BOUNDARY = "audio-boundary";
    // Just above the default 1 MB file limit and below Tomcat's 2 MB max-swallow-size
    private static final int OVERSIZED_FILE_BYTES = 1_200_000;

    @LocalServerPort
    private int port;

    @Test
    void should_returnPayloadTooLargeProblem_when_uploadExceedsLimit() throws Exception {
        // given
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + VOICE_COMMAND_PATH))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartWithFileOf(OVERSIZED_FILE_BYTES)))
                .build();

        // when
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        var problem = JsonMapper.builder().build().readTree(response.body());
        assertThat(problem.path("type").asString()).isEqualTo("about:blank");
        assertThat(problem.path("title").asString()).isEqualTo("Arquivo muito grande");
        assertThat(problem.path("status").asInt()).isEqualTo(413);
        assertThat(problem.path("instance").asString()).isEqualTo(VOICE_COMMAND_PATH);
    }

    private static byte[] multipartWithFileOf(int size) {
        var body = new ByteArrayOutputStream();
        var head = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.mp3\"\r\n"
                + "Content-Type: audio/mpeg\r\n\r\n";
        body.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        body.writeBytes(new byte[size]);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }
}
