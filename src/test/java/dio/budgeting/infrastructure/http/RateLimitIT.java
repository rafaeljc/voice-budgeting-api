package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTIONS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dio.budgeting.domain.UserId;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.ai.AutoConfigureAiStub;
import dio.budgeting.support.security.AccessTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
@AutoConfigureAiStub
@TestPropertySource(
        properties = {
            "budgeting.rate-limit.enabled=true",
            "budgeting.rate-limit.groups.default.max-requests=2",
            "budgeting.rate-limit.groups.default.period=1h",
            "budgeting.rate-limit.groups.ai.max-requests=1",
            "budgeting.rate-limit.groups.ai.period=1h"
        })
class RateLimitIT {
    private static final int DEFAULT_LIMIT = 2;

    private final MockMvc mockMvc;

    RateLimitIT(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_rejectWithTooManyRequests_when_defaultLimitIsExceeded() throws Exception {
        // given
        var userId = randomUser();
        exhaustDefaultLimit(userId);

        // when
        var response = list(userId);

        // then
        response.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:problem-type:budgeting:rate-limit-exceeded"));
        var retryAfter = Long.parseLong(response.andReturn().getResponse().getHeader(RETRY_AFTER));
        assertThat(retryAfter).isBetween(1L, 3600L);
    }

    private void exhaustDefaultLimit(UserId userId) throws Exception {
        for (var i = 0; i < DEFAULT_LIMIT; i++) {
            list(userId).andExpect(status().isOk());
        }
    }

    private ResultActions list(UserId userId) throws Exception {
        return mockMvc.perform(get(TRANSACTIONS_PATH).header(AUTHORIZATION, AccessTokens.authorization(userId)));
    }

    private static UserId randomUser() {
        return new UserId(UUID.randomUUID());
    }
}
