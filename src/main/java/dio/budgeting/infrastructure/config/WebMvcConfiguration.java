package dio.budgeting.infrastructure.config;

import dio.budgeting.infrastructure.http.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<RateLimitInterceptor> rateLimitInterceptor;

    public WebMvcConfiguration(ObjectProvider<RateLimitInterceptor> rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    // The interceptor exists only when rate limiting is enabled
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        rateLimitInterceptor.ifAvailable(registry::addInterceptor);
    }
}
