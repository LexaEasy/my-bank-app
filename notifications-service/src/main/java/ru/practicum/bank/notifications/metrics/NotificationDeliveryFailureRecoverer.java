package ru.practicum.bank.notifications.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import ru.practicum.bank.common.notification.NotificationEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NotificationDeliveryFailureRecoverer implements ConsumerRecordRecoverer {

    static final String METRIC_NAME = "bank.notifications.delivery.failures";
    private static final String UNKNOWN_RECIPIENT = "unknown";

    private final ConsumerRecordRecoverer delegate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public NotificationDeliveryFailureRecoverer(
            ConsumerRecordRecoverer delegate,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper
    ) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        delegate.accept(record, exception);
        meterRegistry.counter(
                METRIC_NAME,
                "recipient_login", recipientLogin(record, exception)
        ).increment();
    }

    private String recipientLogin(ConsumerRecord<?, ?> record, Exception exception) {
        if (record.value() instanceof NotificationEvent event) {
            return validRecipient(event.recipientLogin());
        }
        if (record.value() instanceof byte[] data) {
            return recipientFromJson(data);
        }
        if (record.value() instanceof String data) {
            return recipientFromJson(data.getBytes(StandardCharsets.UTF_8));
        }

        var cause = exception;
        while (cause != null) {
            if (cause instanceof DeserializationException deserializationException) {
                return recipientFromJson(deserializationException.getData());
            }
            cause = cause.getCause() instanceof Exception nested ? nested : null;
        }
        return UNKNOWN_RECIPIENT;
    }

    private String recipientFromJson(byte[] data) {
        if (data == null || data.length == 0) {
            return UNKNOWN_RECIPIENT;
        }
        try {
            return validRecipient(objectMapper.readTree(data).path("recipientLogin").asText());
        } catch (IOException exception) {
            return UNKNOWN_RECIPIENT;
        }
    }

    private String validRecipient(String recipient) {
        return recipient == null || recipient.isBlank() ? UNKNOWN_RECIPIENT : recipient;
    }
}
