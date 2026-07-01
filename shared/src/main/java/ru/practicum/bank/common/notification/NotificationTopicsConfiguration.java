package ru.practicum.bank.common.notification;

import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class NotificationTopicsConfiguration {

    static final int PARTITION_COUNT = 3;
    static final short REPLICATION_FACTOR = 1;
    static final long DLT_RETENTION_MS = Duration.ofDays(7).toMillis();

    @Bean
    NewTopic notificationsTopic(
            @Value("${bank.kafka.notifications-topic}") String topic
    ) {
        return TopicBuilder.name(topic)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    NewTopic notificationsDltTopic(
            @Value("${bank.kafka.notifications-dlt-topic}") String topic
    ) {
        return TopicBuilder.name(topic)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(DLT_RETENTION_MS))
                .build();
    }
}
