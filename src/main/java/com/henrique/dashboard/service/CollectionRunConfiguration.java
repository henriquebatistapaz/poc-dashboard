package com.henrique.dashboard.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CollectionRunConfiguration {
    @Bean(destroyMethod = "shutdown")
    ExecutorService collectionExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
