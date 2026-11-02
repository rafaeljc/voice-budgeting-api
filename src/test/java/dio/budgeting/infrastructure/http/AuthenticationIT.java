package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.log.LogEvents;
import dio.budgeting.support.security.AccessTokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationIT {

    private final MockMvc mockMvc;

    AuthenticationIT(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_returnUnauthenticatedProblem_when_tokenIsMissing() throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH));

        // then
        response.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:unauthenticated"))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Envie um token válido no cabeçalho Authorization."));
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void should_returnUnauthenticatedProblem_when_tokenIsInvalid(String token) throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).header(AUTHORIZATION, "Bearer " + token));

        // then
        response.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:unauthenticated"))
                .andExpect(jsonPath("$.detail").value("Envie um token válido no cabeçalho Authorization."));
    }

    static Stream<String> invalidTokens() {
        var user = AccessTokens.DEFAULT_USER;
        var anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        return Stream.of(
                AccessTokens.sign("another-secret-with-at-least-32-bytes!", AccessTokens.validClaims(user)),
                AccessTokens.sign(
                        AccessTokens.TEST_SECRET, AccessTokens.validClaims(user).expiresAt(anHourAgo)),
                AccessTokens.sign(
                        AccessTokens.TEST_SECRET,
                        JwtClaimsSet.builder().subject(user.uuid().toString())),
                AccessTokens.sign(
                        AccessTokens.TEST_SECRET,
                        JwtClaimsSet.builder().expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))),
                AccessTokens.sign(
                        AccessTokens.TEST_SECRET, AccessTokens.validClaims(user).subject("not-a-uuid")),
                "abc");
    }

    @Test
    void should_authorizeRequest_when_tokenIsValid() throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).header(AUTHORIZATION, AccessTokens.authorization()));

        // then
        response.andExpect(status().isOk());
    }

    @Test
    void should_returnUnauthenticatedProblem_when_routeDoesNotExistAndTokenIsMissing() throws Exception {
        // when
        var response = mockMvc.perform(get("/nowhere"));

        // then
        response.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:unauthenticated"));
    }

    @Test
    void should_answerHealthWithoutToken_when_actuatorHealthIsRequested() throws Exception {
        // when
        var response = mockMvc.perform(get("/actuator/health"));

        // then
        response.andExpect(status().isOk());
    }

    @Test
    void should_returnRequestIdAndLogWarnWithoutToken_when_authenticationFails(CapturedOutput output) throws Exception {
        // given
        var token = AccessTokens.sign(
                "another-secret-with-at-least-32-bytes!", AccessTokens.validClaims(AccessTokens.DEFAULT_USER));

        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).header(AUTHORIZATION, "Bearer " + token))
                .andReturn()
                .getResponse();

        // then
        var requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f]{32}");
        var event = LogEvents.withMessage(output, "Authentication failed");
        assertThat(event.path("log").path("level").asString()).isEqualTo("WARN");
        assertThat(event.path("status").asInt()).isEqualTo(401);
        assertThat(event.path("problemType").asString()).isEqualTo("urn:problem-type:budgeting:unauthenticated");
        assertThat(event.path("traceId").asString()).isEqualTo(requestId);
        assertThat(output.getAll()).doesNotContain(token);
    }
}
