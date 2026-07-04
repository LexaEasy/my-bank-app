package ru.practicum.bank.common.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Locale;

public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationEventPublisher.class);
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String topic;

    public KafkaNotificationEventPublisher(
            KafkaTemplate<String, NotificationEvent> kafkaTemplate,
            MeterRegistry meterRegistry,
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
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
        var errorCategory = errorCategory(exception);
        meterRegistry.counter(
                "bank.kafka.publication.failures",
                "source", event.source().name(),
                "topic", topic,
                "error_category", errorCategory
        ).increment();
        log.error(
                "Notification event send failed: eventId={}, operationId={}, source={}, topic={}, errorCategory={}, errorType={}",
                event.eventId(),
                event.operationId(),
                event.source(),
                topic,
                errorCategory,
                rootCause(exception).getClass().getSimpleName()
        );
    }

    private String errorCategory(Throwable exception) {
        var cause = rootCause(exception);
        var type = cause.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("serializ")) {
            return "serialization";
        }
        if (type.contains("authoriz") || type.contains("authenticat")) {
            return "security";
        }
        return "other";
    }

    private Throwable rootCause(Throwable exception) {
        var cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
