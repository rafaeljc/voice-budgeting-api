package dio.budgeting.support.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
class AiStubConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    WireMockServer aiWireMockServer() {
        return new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }

    @Bean
    DynamicPropertyRegistrar aiBaseUrlRegistrar(WireMockServer aiWireMockServer) {
        return registry -> registry.add("spring.ai.openai.base-url", () -> aiWireMockServer.baseUrl() + "/v1");
    }

    @Bean
    AiStub aiStub(WireMockServer aiWireMockServer) {
        return new OpenAiWireMockStub(aiWireMockServer);
    }
}
