package com.flashflow.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    @Bean
    NewTopic deadLetterTopic(
            @Value("${flashflow.kafka.order-events-topic}") String sourceTopic,
            @Value("${flashflow.kafka.partitions:6}") int partitions
    ) {
        return TopicBuilder.name(sourceTopic + ".DLT")
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }
}
