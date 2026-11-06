package dio.budgeting.domain;

import dio.budgeting.domain.event.DomainEvent;

public interface AuditEntryRepository {
    void append(DomainEvent event);
}
