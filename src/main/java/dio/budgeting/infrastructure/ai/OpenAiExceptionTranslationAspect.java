package dio.budgeting.infrastructure.ai;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import dio.budgeting.application.AiProviderFailedException;
import dio.budgeting.application.AiProviderUnavailableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Translates provider failures into the neutral exceptions of the application layer for every gateway port, so the
 * adapters stay free of error handling (the same idea as Spring's persistence exception translation).
 */
@Aspect
@Component
public class OpenAiExceptionTranslationAspect {

    @Around("execution(* dio.budgeting.application.*Gateway+.*(..))")
    public Object translate(ProceedingJoinPoint gatewayCall) throws Throwable {
        try {
            return gatewayCall.proceed();
        } catch (RuntimeException ex) {
            // Spring AI may wrap the SDK failure; anything that is not a provider failure (e.g. invalid tool
            // arguments) passes through untouched
            var failure = providerFailure(ex);
            if (failure == null) {
                throw ex;
            }
            throw translated(failure);
        }
    }

    private static OpenAIException providerFailure(Throwable ex) {
        for (var cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIException failure) {
                return failure;
            }
        }
        return null;
    }

    // Network failures, rate limiting and provider 5xx may succeed on a later attempt; anything else is on our side
    private static RuntimeException translated(OpenAIException failure) {
        if (failure instanceof OpenAIIoException) {
            return new AiProviderUnavailableException(null, failure);
        }
        if (failure instanceof OpenAIServiceException service
                && (service.statusCode() == HttpStatus.TOO_MANY_REQUESTS.value() || service.statusCode() >= 500)) {
            return new AiProviderUnavailableException(service.statusCode(), failure);
        }
        return new AiProviderFailedException(failure);
    }
}
