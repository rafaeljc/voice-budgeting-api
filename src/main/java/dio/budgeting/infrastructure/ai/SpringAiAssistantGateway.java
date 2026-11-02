package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.AssistantGateway;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.TransactionTools;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SpringAiAssistantGateway implements AssistantGateway {
    private final ChatClient chatClient;

    public SpringAiAssistantGateway(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String interpret(UserId userId, String command) {
        return chatClient
                .prompt()
                .user(command)
                .toolContext(Map.of(TransactionTools.USER_ID, userId))
                .call()
                .content();
    }
}
