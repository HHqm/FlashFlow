package com.flashflow.trade.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfiguration {

    @Bean
    NewTopic orderEventsTopic(
            @Value("${flashflow.kafka.order-events-topic}") String topic,
            @Value("${flashflow.kafka.partitions:6}") int partitions
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
