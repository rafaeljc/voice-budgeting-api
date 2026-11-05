package dio.budgeting.application.output;

import dio.budgeting.application.input.ProposeTransactionInput;

/** What the assistant understood from a command; it never carries text for the user. */
public sealed interface AssistantOutput {
    record Proposal(ProposeTransactionInput proposal) implements AssistantOutput {}

    record Summary(TransactionSummaryOutput summary) implements AssistantOutput {}

    record NotUnderstood() implements AssistantOutput {}
}
