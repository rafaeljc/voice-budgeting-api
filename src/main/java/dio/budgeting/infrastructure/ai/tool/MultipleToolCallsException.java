package dio.budgeting.infrastructure.ai.tool;

public class MultipleToolCallsException extends RuntimeException {
    public MultipleToolCallsException() {
        super("A voice command calls at most one tool");
    }
}
