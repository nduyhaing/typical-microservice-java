package com.shopapp.search.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The topics search relies on, created at start-up when missing (Boot's KafkaAdmin). The CDC topic is compacted
 * (decisions.md 4-72): it keeps the latest row image of every product, so an empty index can be refilled from it;
 * {@code spring.kafka.admin.modify-topic-configs=true} sets {@code cleanup.policy=compact} on a topic Debezium
 * created before (idempotent). Debezium's own topic creation says the same ({@code debezium-product.json}).
 */
@Configuration(proxyBeanMethods = false)
public class CdcTopics {

    @Bean
    NewTopic productCdcTopic(@Value("${search.cdc.topic:dbproduct.public.product}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).compact().build();
    }

    @Bean
    NewTopic productCdcDeadLetterTopic(@Value("${search.cdc.topic:dbproduct.public.product}") String topic) {
        return TopicBuilder.name(topic + "-dlt").partitions(1).replicas(1).build();
    }
}
