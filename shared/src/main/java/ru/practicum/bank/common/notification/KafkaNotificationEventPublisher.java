package ru.practicum.bank.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationEventPublisher.class);

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final String topic;

    public KafkaNotificationEventPublisher(
            KafkaTemplate<String, NotificationEvent> kafkaTemplate,
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(NotificationEvent event) {
        try {
            kafkaTemplate.send(topic, event.recipientLogin(), event)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            logFailure(event, exception);
                            return;
                        }

                        var metadata = result.getRecordMetadata();
                        log.info(
                                "Notification event sent: eventId={}, operationId={}, source={}, topic={}, partition={}, offset={}",
                                event.eventId(),
                                event.operationId(),
                                event.source(),
                                metadata.topic(),
                                metadata.partition(),
                                metadata.offset()
                        );
                    });
        } catch (RuntimeException exception) {
            logFailure(event, exception);
        }
    }

    private void logFailure(NotificationEvent event, Throwable exception) {
        log.error(
                "Notification event send failed: eventId={}, operationId={}, source={}, topic={}, reason={}",
                event.eventId(),
                event.operationId(),
                event.source(),
                topic,
                exception.getMessage(),
                exception
        );
    }
}
