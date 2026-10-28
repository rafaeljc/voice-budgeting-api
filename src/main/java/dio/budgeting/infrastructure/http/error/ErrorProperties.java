package dio.budgeting.infrastructure.http.error;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("budgeting.errors")
record ErrorProperties(Duration assistantUnavailableRetryAfter) {}
