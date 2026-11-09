package dio.budgeting.infrastructure.event;

import dio.budgeting.domain.event.DomainEvent;
import dio.budgeting.domain.event.TransactionProposalCanceledEvent;
import dio.budgeting.domain.event.TransactionProposalConfirmedEvent;
import dio.budgeting.domain.event.TransactionProposalCreatedEvent;
import dio.budgeting.domain.event.VoiceCommandNotUnderstoodEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DomainEventMetricsListener {
    private static final String INTERPRETATIONS = "budgeting.ai.interpretations";

    private final MeterRegistry meterRegistry;

    public DomainEventMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // A plain @EventListener, not a @TransactionalEventListener: the latter would add a publication to the Modulith
    // registry for every event. The counter is incremented before the commit, so a decision whose transaction fails
    // on commit is still counted
    @EventListener
    public void on(DomainEvent event) {
        switch (event) {
            case TransactionProposalConfirmedEvent _ -> count("confirmed");
            case TransactionProposalCanceledEvent _ -> count("canceled");
            case VoiceCommandNotUnderstoodEvent _ -> count("not_understood");
            case TransactionProposalCreatedEvent _ -> {}
        }
    }

    private void count(String outcome) {
        meterRegistry.counter(INTERPRETATIONS, "outcome", outcome).increment();
    }
}
