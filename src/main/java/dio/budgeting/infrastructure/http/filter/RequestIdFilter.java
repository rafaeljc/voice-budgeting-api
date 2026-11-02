package dio.budgeting.infrastructure.http.filter;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Runs right before the Spring Security chain and after the observation filter that creates the span, so a 401
// rejected by the chain also carries the request ID
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public class RequestIdFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final Tracer tracer;

    public RequestIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var span = tracer.currentSpan();
        if (span != null) {
            response.setHeader(REQUEST_ID_HEADER, span.context().traceId());
        }
        filterChain.doFilter(request, response);
    }
}
