package dio.budgeting.infrastructure.scheduling;

import dio.budgeting.infrastructure.config.AuditProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "budgeting.audit.resubmission-enabled", matchIfMissing = true)
public class EventPublicationResubmissionJob {
    private static final Logger log = LoggerFactory.getLogger(EventPublicationResubmissionJob.class);

    private final FailedEventPublications failedPublications;
    private final AuditProperties properties;

    public EventPublicationResubmissionJob(FailedEventPublications failedPublications, AuditProperties properties) {
        this.failedPublications = failedPublications;
        this.properties = properties;
    }

    // Only after the startup succeeds, and only when the resubmission is enabled: its absence in the logs is a clue
    @EventListener(ApplicationReadyEvent.class)
    void logResubmissionSchedule() {
        log.atInfo()
                .addKeyValue("interval", properties.resubmissionInterval())
                .addKeyValue("minAge", properties.resubmissionMinAge())
                .addKeyValue("batchSize", properties.resubmissionBatchSize())
                .log("Event publication resubmission scheduled");
    }

    // Fixed delay, not fixed rate: a slow run never overlaps the next one on this instance. The minimum age keeps the
    // job from resubmitting a publication whose first delivery may still be running
    @Scheduled(fixedDelayString = "${budgeting.audit.resubmission-interval}")
    public void resubmit() {
        failedPublications.resubmit(ResubmissionOptions.defaults()
                .withMinAge(properties.resubmissionMinAge())
                .withBatchSize(properties.resubmissionBatchSize()));
    }
}
