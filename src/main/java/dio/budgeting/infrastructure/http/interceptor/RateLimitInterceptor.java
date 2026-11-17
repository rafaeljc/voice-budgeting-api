package dio.budgeting.infrastructure.http.interceptor;

import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.security.ratelimit.AiRateLimit;
import dio.budgeting.infrastructure.security.ratelimit.RateLimitGroup;
import dio.budgeting.infrastructure.security.ratelimit.UserRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@ConditionalOnBooleanProperty("budgeting.rate-limit.enabled")
public class RateLimitInterceptor implements HandlerInterceptor {
    private final UserRateLimiter rateLimiter;

    public RateLimitInterceptor(UserRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    // Runs after Spring Security and before the controller and its aspects: a refusal reaches neither the AI nor the
    // voice-command circuit. Without a JWT (public routes) there is no user to limit
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            // The decoder only accepts a sub that is a user ID
            var userId = UserId.parse(authentication.getToken().getSubject()).orElseThrow();
            rateLimiter.acquirePermission(userId, groupOf(handler));
        }
        return true;
    }

    private static RateLimitGroup groupOf(Object handler) {
        return handler instanceof HandlerMethod method && method.hasMethodAnnotation(AiRateLimit.class)
                ? RateLimitGroup.AI
                : RateLimitGroup.DEFAULT;
    }
}
