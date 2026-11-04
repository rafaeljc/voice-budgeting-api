package dio.budgeting.application.output;

import dio.budgeting.application.input.ProposeTransactionInput;
import java.util.Optional;

public record AssistantOutput(String text, Optional<ProposeTransactionInput> proposal) {}
