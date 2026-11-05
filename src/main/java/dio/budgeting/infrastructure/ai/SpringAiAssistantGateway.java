package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.AssistantGateway;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.InterpretationCollector;
import dio.budgeting.infrastructure.ai.tool.TransactionTools;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

@Component
public class SpringAiAssistantGateway implements AssistantGateway {
    private final ChatClient chatClient;
    private final PromptTemplate userMessageTemplate;

    public SpringAiAssistantGateway(ChatClient chatClient, PromptTemplate userMessageTemplate) {
        this.chatClient = chatClient;
        this.userMessageTemplate = userMessageTemplate;
    }

    @Override
    public AssistantOutput interpret(UserId userId, String command) {
        var collector = new InterpretationCollector();
        chatClient
                .prompt()
                .user(userMessage(command))
                .toolContext(
                        Map.of(TransactionTools.USER_ID, userId, TransactionTools.INTERPRETATION_COLLECTOR, collector))
                .call()
                .content();
        return collector.interpretation();
    }

    // Without < and >, speech cannot form or close the delimiter tags around the transcription
    private String userMessage(String command) {
        return userMessageTemplate.render(Map.of("transcription", command.replaceAll("[<>]", "")));
    }
}
