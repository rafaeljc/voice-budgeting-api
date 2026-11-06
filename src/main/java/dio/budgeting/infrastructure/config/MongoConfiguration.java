package dio.budgeting.infrastructure.config;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class MongoConfiguration {

    @Bean
    public MongoClientSettingsBuilderCustomizer auditTimeoutsCustomizer(AuditProperties properties) {
        return builder -> builder.applyToSocketSettings(socket -> socket.connectTimeout(
                                properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(
                        properties.serverSelectionTimeout().toMillis(), TimeUnit.MILLISECONDS));
    }
}
