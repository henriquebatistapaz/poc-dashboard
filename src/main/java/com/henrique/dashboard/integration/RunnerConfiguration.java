package com.henrique.dashboard.integration;

import com.example.collectorrunner.integration.*;
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
    CollectionGateway collectionGateway() { return DefaultCollectionGateway.fromEnvironment(System.getenv()); }

    @Bean
    CollectionRunner collectionRunner(CollectionGateway gateway, CollectedItemWriter writer) {
        return new CollectionRunner(gateway, writer);
    }
}
