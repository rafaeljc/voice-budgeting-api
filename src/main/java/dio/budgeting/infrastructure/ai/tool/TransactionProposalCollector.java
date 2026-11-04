package dio.budgeting.infrastructure.ai.tool;

import dio.budgeting.application.input.ProposeTransactionInput;
import java.util.Optional;

/** Holds what the model proposed during one command; the tool only notes it, the use case saves it. */
public class TransactionProposalCollector {
    private ProposeTransactionInput proposal;

    public void collect(ProposeTransactionInput proposal) {
        this.proposal = proposal;
    }

    public Optional<ProposeTransactionInput> proposal() {
        return Optional.ofNullable(proposal);
    }
}
