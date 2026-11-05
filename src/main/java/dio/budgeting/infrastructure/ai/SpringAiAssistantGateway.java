package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.AssistantGateway;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.tool.TransactionProposalCollector;
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
    public AssistantOutput interpret(UserId userId, String command) {
        var collector = new TransactionProposalCollector();
        chatClient
                .prompt()
                .user(command)
                .toolContext(Map.of(TransactionTools.USER_ID, userId, TransactionTools.PROPOSAL_COLLECTOR, collector))
                .call()
                .content();
        return collector
                .proposal()
                .<AssistantOutput>map(AssistantOutput.Proposal::new)
                .orElseGet(AssistantOutput.NotUnderstood::new);
    }
}
