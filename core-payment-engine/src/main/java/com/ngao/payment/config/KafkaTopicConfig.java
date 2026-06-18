package com.ngao.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the {@code core-transactions} topic so Spring Kafka's KafkaAdmin
 * provisions it on the broker at startup (idempotently).
 */
@Configuration
public class KafkaTopicConfig {

    public static final String CORE_TRANSACTIONS_TOPIC = "core-transactions";

    @Bean
    public NewTopic coreTransactionsTopic() {
        return TopicBuilder.name(CORE_TRANSACTIONS_TOPIC)
                .partitions(3)
                .replicas(1)   // single-broker dev cluster
                .build();
    }
}
