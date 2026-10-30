package dio.budgeting.domain;

public record PageQuery(int number, int size) {
    public PageQuery {
        if (number < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }
    }
}
