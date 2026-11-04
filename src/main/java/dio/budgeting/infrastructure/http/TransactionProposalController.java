package dio.budgeting.infrastructure.http;

import dio.budgeting.application.CancelTransactionProposalUseCase;
import dio.budgeting.application.ConfirmTransactionProposalUseCase;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.TransactionProposalNotFoundException;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transaction-proposals")
public class TransactionProposalController {
    private final ConfirmTransactionProposalUseCase confirmTransactionProposalUseCase;
    private final CancelTransactionProposalUseCase cancelTransactionProposalUseCase;

    public TransactionProposalController(
            ConfirmTransactionProposalUseCase confirmTransactionProposalUseCase,
            CancelTransactionProposalUseCase cancelTransactionProposalUseCase) {
        this.confirmTransactionProposalUseCase = confirmTransactionProposalUseCase;
        this.cancelTransactionProposalUseCase = cancelTransactionProposalUseCase;
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<TransactionResponse> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        var response = TransactionResponse.from(confirmTransactionProposalUseCase.execute(userId(jwt), proposalId(id)));
        return ResponseEntity.created(transactionLocation(response.id())).body(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        cancelTransactionProposalUseCase.execute(userId(jwt), proposalId(id));
        return ResponseEntity.noContent().build();
    }

    // The token was already validated: its sub is a UUID
    private static UserId userId(Jwt jwt) {
        return UserId.parse(jwt.getSubject()).orElseThrow();
    }

    // A malformed ID is as unknown as any other: the edge rejects it before the use case
    private static TransactionProposalId proposalId(String id) {
        return TransactionProposalId.parse(id).orElseThrow(TransactionProposalNotFoundException::new);
    }

    // The transaction lives under its own resource, not under the path of this request
    private static URI transactionLocation(String transactionId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/transactions/{id}")
                .buildAndExpand(transactionId)
                .toUri();
    }
}
