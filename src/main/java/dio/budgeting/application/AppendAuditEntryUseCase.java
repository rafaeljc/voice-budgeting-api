package dio.budgeting.application;

import dio.budgeting.domain.AuditEntryRepository;
import dio.budgeting.domain.event.DomainEvent;
import org.springframework.stereotype.Service;

@Service
public class AppendAuditEntryUseCase {
    private final AuditEntryRepository auditEntryRepository;

    public AppendAuditEntryUseCase(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    public void execute(DomainEvent event) {
        auditEntryRepository.append(event);
    }
}
