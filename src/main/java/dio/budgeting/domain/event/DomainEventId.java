package dio.budgeting.domain.event;

import java.util.UUID;

public record DomainEventId(UUID uuid) {
    public DomainEventId {
        if (uuid == null) {
            throw new IllegalArgumentException("DomainEventId uuid must not be null");
        }
    }

    public DomainEventId() {
        this(UUID.randomUUID());
    }
}
