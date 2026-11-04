package dio.budgeting.infrastructure.http;

import dio.budgeting.application.GetTransactionUseCase;
import dio.budgeting.application.ListTransactionsUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.ProcessVoiceCommandUseCase;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionNotFoundException;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.http.request.ListTransactionsRequest;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.request.VoiceCommandRequest;
import dio.budgeting.infrastructure.http.response.TransactionPageResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import dio.budgeting.infrastructure.http.response.VoiceCommandResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsUseCase listTransactionsUseCase;
    private final GetTransactionUseCase getTransactionUseCase;

    private final ProcessVoiceCommandUseCase processVoiceCommandUseCase;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsUseCase listTransactionsUseCase,
            GetTransactionUseCase getTransactionUseCase,
            ProcessVoiceCommandUseCase processVoiceCommandUseCase) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsUseCase = listTransactionsUseCase;
        this.getTransactionUseCase = getTransactionUseCase;
        this.processVoiceCommandUseCase = processVoiceCommandUseCase;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransactionRequest request) {
        var response = TransactionResponse.from(persistTransactionUseCase.execute(userId(jwt), request.toInput()));
        return ResponseEntity.created(locationOf(response.id())).body(response);
    }

    // The token was already validated: its sub is a UUID
    private static UserId userId(Jwt jwt) {
        return UserId.parse(jwt.getSubject()).orElseThrow();
    }

    private static URI locationOf(String id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

    @GetMapping
    public TransactionPageResponse listTransactions(
            @AuthenticationPrincipal Jwt jwt, @Valid ListTransactionsRequest request) {
        return TransactionPageResponse.from(listTransactionsUseCase.execute(userId(jwt), request.toInput()));
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        // A malformed ID is as unknown as any other: the edge rejects it before the use case
        var transactionId = TransactionId.parse(id).orElseThrow(TransactionNotFoundException::new);
        return TransactionResponse.from(getTransactionUseCase.execute(userId(jwt), transactionId));
    }

    @PostMapping(
            value = "/ai",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    VoiceCommandResponse transcribe(@AuthenticationPrincipal Jwt jwt, @Valid VoiceCommandRequest request)
            throws IOException {
        var input = request.toInput();
        log.atInfo()
                .addKeyValue("audioSizeBytes", input.audio().length)
                .addKeyValue("audioFormat", input.format())
                .log("Voice command received");

        return VoiceCommandResponse.from(processVoiceCommandUseCase.execute(userId(jwt), input));
    }
}
