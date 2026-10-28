package dio.budgeting.infrastructure.http.filter;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
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
