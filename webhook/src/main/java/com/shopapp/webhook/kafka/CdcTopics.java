package com.shopapp.webhook.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * webhook's own dead-letter topics, created at start-up when missing. The CDC topics themselves are created by
 * Debezium (and by search for the product topic) with their compaction settings; webhook does not declare them, so
 * it never creates the order topic with the wrong configuration before the connector does.
 */
@Configuration(proxyBeanMethods = false)
public class CdcTopics {

    @Bean
    NewTopic productDeadLetterTopic(@Value("${shopapp.webhook.cdc.product-topic}") String topic) {
        return TopicBuilder.name(CdcErrorHandling.deadLetterTopic(topic)).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic orderDeadLetterTopic(@Value("${shopapp.webhook.cdc.order-topic}") String topic) {
        return TopicBuilder.name(CdcErrorHandling.deadLetterTopic(topic)).partitions(1).replicas(1).build();
    }
}
