package ru.practicum.bank.common.notification;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KafkaNotificationEventPublisherTest {

    private static final String TOPIC = "bank.notifications";

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaNotificationEventPublisher publisher =
            new KafkaNotificationEventPublisher(kafkaTemplate, TOPIC);

    @Test
    void shouldSendEventWithRecipientLoginAsMessageKey() {
        var event = event();
        var future = new CompletableFuture<SendResult<String, NotificationEvent>>();
        var producerRecord = new ProducerRecord<>(TOPIC, event.recipientLogin(), event);
        var metadata = new RecordMetadata(new TopicPartition(TOPIC, 1), 0, 0, 0, 0, 0);
        when(kafkaTemplate.send(TOPIC, event.recipientLogin(), event)).thenReturn(future);

        publisher.publish(event);
        future.complete(new SendResult<>(producerRecord, metadata));

        verify(kafkaTemplate).send(TOPIC, event.recipientLogin(), event);
    }

    @Test
    void shouldNotPropagateAsynchronousSendFailure() {
        var event = event();
        var future = new CompletableFuture<SendResult<String, NotificationEvent>>();
        when(kafkaTemplate.send(TOPIC, event.recipientLogin(), event)).thenReturn(future);

        assertThatCode(() -> {
            publisher.publish(event);
            future.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldNotPropagateSynchronousSendFailure() {
        var event = event();
        when(kafkaTemplate.send(TOPIC, event.recipientLogin(), event))
                .thenThrow(new IllegalStateException("Kafka unavailable"));

        assertThatCode(() -> publisher.publish(event))
                .doesNotThrowAnyException();
    }

    private NotificationEvent event() {
        return new NotificationEvent(
                UUID.fromString("10cb8eb2-b488-4f62-b139-a07314cc3ef4"),
                UUID.fromString("3e3a3fec-843e-44e2-bcf5-3bea12845327"),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                "ivan",
                "Счёт пополнен",
                Instant.parse("2026-06-30T05:00:00Z"),
                new BigDecimal("250.00"),
                Currency.RUB
        );
    }
}
