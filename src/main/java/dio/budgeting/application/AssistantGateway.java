package dio.budgeting.application;

import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.domain.UserId;

public interface AssistantGateway {
    AssistantOutput interpret(UserId userId, String command);
}
