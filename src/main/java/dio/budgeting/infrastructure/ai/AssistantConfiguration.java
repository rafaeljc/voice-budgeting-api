package dio.budgeting.infrastructure.ai;

import dio.budgeting.infrastructure.ai.tool.TransactionTools;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class AssistantConfiguration {

    @Bean
    public ChatClient assistantChatClient(
            ChatClient.Builder chatClientBuilder,
            TransactionTools transactionTools,
            @Value("classpath:prompts/system-message.st") Resource systemPrompt)
            throws IOException {
        return chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultTools(transactionTools)
                .build();
    }

    @Bean
    public PromptTemplate userMessageTemplate(@Value("classpath:prompts/user-message.st") Resource userMessage) {
        return new PromptTemplate(userMessage);
    }
}
