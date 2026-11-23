package dev.henrique.dashboard.integration;

import dev.henrique.collectorrunner.integration.*;
import dev.henrique.dashboard.repository.ItemRemovalRepository;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RunnerConfiguration {
    @Bean
    CollectedItemWriter collectedItemWriter(DataSource dataSource) { return new CollectedItemWriter(dataSource); }

    @Bean
    @ConditionalOnMissingBean(CollectionGateway.class)
    CollectionGateway collectionGateway(ItemRemovalRepository removals) {
        return new RemovalAwareCollectionGateway(DefaultCollectionGateway.fromEnvironment(System.getenv()), removals);
    }

    @Bean
    CollectionRunner collectionRunner(CollectionGateway gateway, CollectedItemWriter writer) {
        return new CollectionRunner(gateway, writer);
    }
}
