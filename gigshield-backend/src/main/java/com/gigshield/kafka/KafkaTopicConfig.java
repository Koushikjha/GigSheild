// com/gigshield/kafka/KafkaTopicConfig.java
package com.gigshield.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares GigShield's Kafka topics. Spring Boot's KafkaAdmin auto-creates
 * these on startup against the configured broker (idempotent — a no-op if
 * they already exist).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic disruptionEventsTopic() {
        return TopicBuilder.name(KafkaTopics.DISRUPTION_EVENT_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic payoutRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYOUT_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic payoutCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYOUT_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
