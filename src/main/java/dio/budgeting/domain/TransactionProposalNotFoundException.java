package dio.budgeting.domain;

public class TransactionProposalNotFoundException extends RuntimeException {
    public TransactionProposalNotFoundException() {
        super("Transaction proposal not found");
    }
}
