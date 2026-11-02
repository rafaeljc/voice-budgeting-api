package dio.budgeting.application;

import dio.budgeting.domain.UserId;

public interface AssistantGateway {
    String interpret(UserId userId, String command);
}
