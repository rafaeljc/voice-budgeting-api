package dio.budgeting.support.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

// The circuits live in the context shared by the tests, so one test's failures would open the circuit of the next
public class ResetCircuitBreakerExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(CircuitBreakerRegistry.class)
                .getAllCircuitBreakers()
                .forEach(circuitBreaker -> circuitBreaker.reset());
    }
}
