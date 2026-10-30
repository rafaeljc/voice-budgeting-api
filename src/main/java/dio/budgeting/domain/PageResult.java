package dio.budgeting.domain;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> content, int number, int size, long totalElements) {
    public PageResult {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return (int) Math.ceilDiv(totalElements, size);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(content.stream().<R>map(mapper).toList(), number, size, totalElements);
    }
}
