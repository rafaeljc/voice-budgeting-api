package dio.budgeting.domain;

import java.time.Duration;

public record TransactionProposalTimeToLive(Duration value) {
    public TransactionProposalTimeToLive {
        if (value == null) {
            throw new IllegalArgumentException("TransactionProposalTimeToLive value must not be null");
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("TransactionProposalTimeToLive value must be greater than zero");
        }
    }
}
