package dio.budgeting.infrastructure.event;

import dio.budgeting.infrastructure.ai.event.AiUsageMeasuredEvent;
import dio.budgeting.infrastructure.persistence.document.AiUsageEntryDocument;
import dio.budgeting.infrastructure.persistence.repository.AiUsageEntryDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AiUsageMeasuredEventListener {
    private static final Logger log = LoggerFactory.getLogger(AiUsageMeasuredEventListener.class);

    private final AiUsageEntryDocumentRepository documentRepository;

    public AiUsageMeasuredEventListener(AiUsageEntryDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    // Async and best effort: the write never holds the request nor fails it when MongoDB is down, and a plain
    // @EventListener keeps the event out of the Modulith publication registry, so a lost entry is not resubmitted
    @Async
    @EventListener
    public void on(AiUsageMeasuredEvent event) {
        try {
            documentRepository.insert(AiUsageEntryDocument.from(event));
        } catch (RuntimeException e) {
            // Only types, never the event content or the exception message
            log.atWarn()
                    .addKeyValue("stage", event.usage().getClass().getSimpleName())
                    .addKeyValue("errorType", e.getClass().getSimpleName())
                    .log("AI usage entry not recorded");
        }
    }
}
