package dio.budgeting.infrastructure.config;

import dio.budgeting.domain.TransactionProposalTimeToLive;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TransactionProposalProperties.class)
public class TransactionProposalConfiguration {

    @Bean
    public TransactionProposalTimeToLive transactionProposalTimeToLive(TransactionProposalProperties properties) {
        return new TransactionProposalTimeToLive(properties.timeToLive());
    }
}
