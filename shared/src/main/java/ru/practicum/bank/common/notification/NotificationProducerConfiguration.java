package ru.practicum.bank.common.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
@Import(NotificationTopicsConfiguration.class)
public class NotificationProducerConfiguration {

    @Bean
    @Lazy
    NotificationEventPublisher notificationEventPublisher(
            KafkaTemplate<String, NotificationEvent> kafkaTemplate,
            @Value("${bank.kafka.notifications-topic}") String topic
    ) {
        return new KafkaNotificationEventPublisher(kafkaTemplate, topic);
    }
}
