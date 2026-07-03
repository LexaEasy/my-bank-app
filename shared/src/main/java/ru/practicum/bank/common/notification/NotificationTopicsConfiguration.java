package ru.practicum.bank.common.notification;

import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationTopicsProperties.class)
public class NotificationTopicsConfiguration {

    @Bean
    NewTopic notificationsTopic(NotificationTopicsProperties properties) {
        return TopicBuilder.name(properties.getNotificationsTopic())
                .partitions(properties.getNotificationsPartitions())
                .replicas(properties.getNotificationsReplicationFactor())
                .build();
    }

    @Bean
    NewTopic notificationsDltTopic(NotificationTopicsProperties properties) {
        return TopicBuilder.name(properties.getNotificationsDltTopic())
                .partitions(properties.getNotificationsDltPartitions())
                .replicas(properties.getNotificationsReplicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(properties.getNotificationsDltRetentionMs()))
                .build();
    }
}
