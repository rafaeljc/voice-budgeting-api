package dio.budgeting.infrastructure.http.error;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import dio.budgeting.domain.TransactionNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

@RestControllerAdvice
@EnableConfigurationProperties(ErrorProperties.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final URI BLANK_TYPE = URI.create("about:blank");
    private static final String ERRORS_PROPERTY = "errors";
    private static final String INVALID_VALUE_CODE = "problem.invalid-value";
    private static final String UNEXPECTED_ERROR_CODE = "problem.unexpected-error";

    private final ErrorProperties properties;

    public ApiExceptionHandler(ErrorProperties properties) {
        this.properties = properties;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var inBody = ex.getParameter().hasParameterAnnotation(RequestBody.class);
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    var detail = error.isBindingFailure() ? message(INVALID_VALUE_CODE) : error.getDefaultMessage();
                    return inBody
                            ? InvalidField.inBody(error.getField(), detail)
                            : InvalidField.inRequest(error.getField(), detail);
                })
                .toList();
        return handleExceptionInternal(
                ex, problem(ProblemType.VALIDATION_ERROR, status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof DatabindException cause && !cause.getPath().isEmpty()) {
            var errors = List.of(InvalidField.inBody(jsonPath(cause), message(INVALID_VALUE_CODE)));
            return handleExceptionInternal(
                    ex, problem(ProblemType.VALIDATION_ERROR, status, errors), headers, status, request);
        }
        return handleExceptionInternal(
                ex, problem(ProblemType.MALFORMED_REQUEST, status, null), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var name =
                ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        var errors = List.of(InvalidField.inRequest(name, message(INVALID_VALUE_CODE)));
        return handleExceptionInternal(
                ex, problem(ProblemType.VALIDATION_ERROR, status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = List.of(InvalidField.inRequest(ex.getRequestPartName(), message("problem.required-part")));
        return handleExceptionInternal(
                ex, problem(ProblemType.VALIDATION_ERROR, status, errors), headers, status, request);
    }

    // Tool arguments the model got wrong: the request is valid, the interpreted content is not
    @ExceptionHandler({ConstraintViolationException.class, DatabindException.class})
    ResponseEntity<Object> handleInvalidCommand(Exception ex, WebRequest request) {
        var status = HttpStatus.UNPROCESSABLE_CONTENT;
        return handleExceptionInternal(
                ex, problem(ProblemType.INVALID_COMMAND, status, null), new HttpHeaders(), status, request);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ResponseEntity<Object> handleTransactionNotFound(TransactionNotFoundException ex, WebRequest request) {
        var status = HttpStatus.NOT_FOUND;
        return handleExceptionInternal(
                ex, problem(ProblemType.TRANSACTION_NOT_FOUND, status, null), new HttpHeaders(), status, request);
    }

    @ExceptionHandler(OpenAIException.class)
    ResponseEntity<Object> handleAiProviderFailure(OpenAIException ex, WebRequest request) {
        if (!isTransient(ex)) {
            return handleUnexpected(ex, request);
        }
        var status = HttpStatus.SERVICE_UNAVAILABLE;
        var headers = new HttpHeaders();
        headers.set(
                HttpHeaders.RETRY_AFTER,
                String.valueOf(properties.assistantUnavailableRetryAfter().toSeconds()));
        return handleExceptionInternal(
                ex, problem(ProblemType.ASSISTANT_UNAVAILABLE, status, null), headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle(message(UNEXPECTED_ERROR_CODE + ".title"));
        problem.setDetail(message(UNEXPECTED_ERROR_CODE + ".detail"));
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        var response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        // Spring leaves the type unset when no message code resolves it; the contract says about:blank explicitly
        if (response.getBody() instanceof ProblemDetail problem && problem.getType() == null) {
            problem.setType(BLANK_TYPE);
        }
        if (ex instanceof OpenAIException providerFailure && isTransient(providerFailure)) {
            logProviderUnavailable(statusCode, providerFailure);
        } else if (statusCode.is5xxServerError()) {
            log.atError().setCause(ex).log("Unexpected error");
        } else if (response.getBody() instanceof ProblemDetail problem) {
            logRejected(ex, statusCode, problem);
        }
        return response;
    }

    // Network failures, rate limiting and provider 5xx may succeed on a later attempt; anything else is on our side
    private static boolean isTransient(OpenAIException ex) {
        return ex instanceof OpenAIIoException
                || ex instanceof OpenAIServiceException service
                        && (service.statusCode() == HttpStatus.TOO_MANY_REQUESTS.value()
                                || service.statusCode() >= 500);
    }

    private void logProviderUnavailable(HttpStatusCode status, OpenAIException ex) {
        var event = log.atWarn().addKeyValue("status", status.value());
        if (ex instanceof OpenAIServiceException service) {
            event = event.addKeyValue("providerStatus", service.statusCode());
        }
        event.log("AI provider unavailable");
    }

    private void logRejected(Exception ex, HttpStatusCode status, ProblemDetail problem) {
        var event = log.atInfo()
                .addKeyValue("status", status.value())
                .addKeyValue("problemType", problem.getType().toString());
        var invalidFields = invalidFields(ex, problem);
        if (invalidFields != null) {
            event = event.addKeyValue("invalidFields", invalidFields);
        }
        event.log("Request rejected");
    }

    // Field names only: the rejected values are business data and never reach the log
    private static List<String> invalidFields(Exception ex, ProblemDetail problem) {
        if (ex instanceof ConstraintViolationException violation) {
            return violation.getConstraintViolations().stream()
                    .map(constraint -> lastNodeName(constraint.getPropertyPath()))
                    .distinct()
                    .toList();
        }
        if (ex instanceof DatabindException databind) {
            return databind.getPath().stream()
                    .map(reference -> reference.getPropertyName())
                    .filter(Objects::nonNull)
                    .reduce((first, last) -> last)
                    .stream()
                    .toList();
        }
        if (problem.getProperties() != null && problem.getProperties().get(ERRORS_PROPERTY) instanceof List<?> errors) {
            return errors.stream().map(error -> ((InvalidField) error).name()).toList();
        }
        return null;
    }

    private static String lastNodeName(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                .map(node -> node.getName())
                .filter(Objects::nonNull)
                .reduce((first, last) -> last)
                .orElse("");
    }

    private ProblemDetail problem(ProblemType type, HttpStatusCode status, List<InvalidField> errors) {
        var problem = ProblemDetail.forStatus(status);
        problem.setType(type.uri());
        problem.setTitle(message(type.messageCode() + ".title"));
        problem.setDetail(message(type.messageCode() + ".detail"));
        if (errors != null) {
            problem.setProperty(ERRORS_PROPERTY, errors);
        }
        return problem;
    }

    private String message(String code) {
        return getMessageSource().getMessage(code, null, LocaleContextHolder.getLocale());
    }

    private static String jsonPath(JacksonException exception) {
        return exception.getPath().stream()
                .map(reference -> reference.getPropertyName() != null
                        ? reference.getPropertyName()
                        : String.valueOf(reference.getIndex()))
                .collect(Collectors.joining("."));
    }
}
