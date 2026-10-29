package dio.budgeting.infrastructure.ai.tool;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DatabindException;

@Configuration
public class ToolExecutionConfiguration {

    // Invalid arguments reach the advice unwrapped; any other tool failure is never handed to the model as text
    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(true)
                .rethrowExceptions(List.of(ConstraintViolationException.class, DatabindException.class))
                .build();
    }
}
