package dio.budgeting.infrastructure.http;

import dio.budgeting.application.GetTransactionUseCase;
import dio.budgeting.application.ListTransactionsUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.infrastructure.ai.tool.TransactionTools;
import dio.budgeting.infrastructure.http.request.ListTransactionsRequest;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionPageResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsUseCase listTransactionsUseCase;
    private final GetTransactionUseCase getTransactionUseCase;

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsUseCase listTransactionsUseCase,
            GetTransactionUseCase getTransactionUseCase,
            TranscriptionModel transcriptionModel,
            TransactionTools transactionTools,
            @Value("classpath:prompts/system-message.st") Resource systemPrompt,
            ChatClient.Builder chatClientBuilder,
            TextToSpeechModel textToSpeechModel)
            throws IOException {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsUseCase = listTransactionsUseCase;
        this.getTransactionUseCase = getTransactionUseCase;
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultTools(transactionTools)
                .build();
        this.textToSpeechModel = textToSpeechModel;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        var response = TransactionResponse.from(persistTransactionUseCase.execute(request.toInput()));
        return ResponseEntity.created(locationOf(response.id())).body(response);
    }

    private static URI locationOf(String id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

    @GetMapping
    public TransactionPageResponse listTransactions(@Valid ListTransactionsRequest request) {
        return TransactionPageResponse.from(listTransactionsUseCase.execute(request.toInput()));
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable String id) {
        return TransactionResponse.from(getTransactionUseCase.execute(id));
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mpeg")
    ResponseEntity<Resource> transcribe(@RequestParam("file") MultipartFile file) {
        log.atInfo()
                .addKeyValue("audioSizeBytes", file.getSize())
                .addKeyValue("audioContentType", file.getContentType())
                .log("Voice command received");

        var userMessage = transcriptionModel.transcribe(file.getResource());
        log.atInfo().addKeyValue("transcriptionLength", userMessage.length()).log("Audio transcribed");

        var result = chatClient.prompt().user(userMessage).call().content();
        log.atInfo().addKeyValue("answerLength", result.length()).log("Assistant answered");

        byte[] audio = textToSpeechModel.call(result);
        log.atInfo().addKeyValue("speechSizeBytes", audio.length).log("Speech synthesized");
        var resource = new ByteArrayResource(audio);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("audio.mp3")
                                .build()
                                .toString())
                .body(resource);
    }
}
