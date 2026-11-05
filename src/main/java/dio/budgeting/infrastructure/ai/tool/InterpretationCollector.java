package dio.budgeting.infrastructure.ai.tool;

import dio.budgeting.application.output.AssistantOutput;

/** Holds what the model asked for during one command; a command calls at most one tool, once. */
public class InterpretationCollector {
    private AssistantOutput interpretation;

    public void note(AssistantOutput noted) {
        if (interpretation != null) {
            throw new MultipleToolCallsException();
        }
        interpretation = noted;
    }

    public AssistantOutput interpretation() {
        return interpretation == null ? new AssistantOutput.NotUnderstood() : interpretation;
    }
}
