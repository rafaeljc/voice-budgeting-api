package dio.budgeting.infrastructure.http.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
record InvalidField(String pointer, String parameter, String detail) {
    static InvalidField inBody(String field, String detail) {
        return new InvalidField("#/" + field.replace('.', '/'), null, detail);
    }

    static InvalidField inRequest(String parameter, String detail) {
        return new InvalidField(null, parameter, detail);
    }

    String name() {
        return pointer != null ? pointer : parameter;
    }
}
