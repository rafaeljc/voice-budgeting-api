package dio.budgeting.infrastructure.http.error;

import java.net.URI;

enum ProblemType {
    VALIDATION_ERROR("validation-error"),
    MALFORMED_REQUEST("malformed-request"),
    ASSISTANT_UNAVAILABLE("assistant-unavailable");

    private static final String URN_PREFIX = "urn:problem-type:budgeting:";

    private final String slug;

    ProblemType(String slug) {
        this.slug = slug;
    }

    URI uri() {
        return URI.create(URN_PREFIX + slug);
    }

    String messageCode() {
        return "problem." + slug;
    }
}
