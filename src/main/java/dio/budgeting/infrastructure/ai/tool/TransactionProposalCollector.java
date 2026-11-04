package dio.budgeting.infrastructure.ai.tool;

import dio.budgeting.application.input.ProposeTransactionInput;
import java.util.Optional;

/** Holds what the model proposed during one command; the tool only notes it, the use case saves it. */
public class TransactionProposalCollector {
    private ProposeTransactionInput proposal;

    /** A repeat of the same proposal is a no-op; a different one is refused, since a command proposes one. */
    public ProposeTransactionInput collect(ProposeTransactionInput proposed) {
        var normalized = normalize(proposed);
        if (proposal != null && !proposal.equals(normalized)) {
            throw new MultipleTransactionProposalsException();
        }
        proposal = normalized;
        return proposal;
    }

    public Optional<ProposeTransactionInput> proposal() {
        return Optional.ofNullable(proposal);
    }

    // Same normalization the domain applies, so "Mercado" and " Mercado " with 80 and 80.00 are the same proposal
    private static ProposeTransactionInput normalize(ProposeTransactionInput proposed) {
        return new ProposeTransactionInput(
                proposed.description().strip(), proposed.toMoney().value(), proposed.category());
    }
}
