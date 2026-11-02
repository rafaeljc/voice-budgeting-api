package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.security.AccessTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
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
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:unauthenticated"))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Envie um token válido no cabeçalho Authorization."));
    }

    @Test
    void should_authorizeRequest_when_tokenIsValid() throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSACTIONS_PATH).header(AUTHORIZATION, AccessTokens.authorization()));

        // then
        response.andExpect(status().isOk());
    }
}
