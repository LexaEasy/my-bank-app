package ru.practicum.bank.notifications.messaging;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.notifications.service.NotificationService;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final Validator validator;

    public NotificationEventListener(NotificationService notificationService, Validator validator) {
        this.notificationService = notificationService;
        this.validator = validator;
    }

    @KafkaListener(topics = "${bank.kafka.notifications-topic}")
    public void listen(ConsumerRecord<String, NotificationEvent> record) {
        NotificationEvent event = record.value();
        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        log.info(
                "Kafka notification received: topic={}, partition={}, offset={}, eventId={}",
                record.topic(),
                record.partition(),
                record.offset(),
                event.eventId()
        );
        notificationService.notify(event);
    }
}
