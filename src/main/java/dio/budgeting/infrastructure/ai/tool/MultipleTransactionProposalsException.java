package dio.budgeting.infrastructure.ai.tool;

public class MultipleTransactionProposalsException extends RuntimeException {
    public MultipleTransactionProposalsException() {
        super("A voice command proposes at most one transaction");
    }
}
