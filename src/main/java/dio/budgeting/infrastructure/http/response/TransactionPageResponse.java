package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.PageResult;
import java.util.List;

public record TransactionPageResponse(List<TransactionResponse> content, PageMetadata page) {
    public static TransactionPageResponse from(PageResult<TransactionOutput> result) {
        return new TransactionPageResponse(
                result.content().stream().map(TransactionResponse::from).toList(),
                new PageMetadata(result.size(), result.number(), result.totalElements(), result.totalPages()));
    }

    public record PageMetadata(int size, int number, long totalElements, int totalPages) {}
}
