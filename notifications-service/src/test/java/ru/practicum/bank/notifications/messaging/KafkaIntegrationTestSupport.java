package ru.practicum.bank.notifications.messaging;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

abstract class KafkaIntegrationTestSupport {

    static final String TOPIC = "bank.notifications";
    static final String DLT_TOPIC = "bank.notifications.dlt";
    static final String GROUP_ID = "bank-notifications-integration";

    @Autowired
    EmbeddedKafkaBroker broker;

    private final List<DefaultKafkaProducerFactory<?, ?>> producerFactories = new ArrayList<>();

    @AfterEach
    void closeProducerFactories() {
        producerFactories.forEach(DefaultKafkaProducerFactory::destroy);
        producerFactories.clear();
    }

    KafkaTemplate<String, NotificationEvent> eventTemplate() {
        var properties = KafkaTestUtils.producerProps(broker);
        DefaultKafkaProducerFactory<String, NotificationEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(
                properties,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new JsonSerializer<>()
        );
        producerFactories.add(producerFactory);
        return new KafkaTemplate<>(producerFactory);
    }

    KafkaTemplate<String, byte[]> byteTemplate() {
        var properties = KafkaTestUtils.producerProps(broker);
        DefaultKafkaProducerFactory<String, byte[]> producerFactory =
                new DefaultKafkaProducerFactory<>(
                properties,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.ByteArraySerializer()
        );
        producerFactories.add(producerFactory);
        return new KafkaTemplate<>(producerFactory);
    }

    Consumer<String, byte[]> dltConsumer(String groupId) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(groupId, "false", broker);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumer = new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new ByteArrayDeserializer()
        ).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);
        return consumer;
    }

    ConsumerRecord<String, byte[]> awaitRecord(
            Consumer<String, byte[]> consumer,
            Predicate<ConsumerRecord<String, byte[]>> predicate
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(250))) {
                if (predicate.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("Expected Kafka record was not received");
    }

    long committedOffset(int partition) throws Exception {
        try (var admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()
        ))) {
            var offset = admin.listConsumerGroupOffsets(GROUP_ID)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS)
                    .get(new TopicPartition(TOPIC, partition));
            return offset == null ? -1L : offset.offset();
        }
    }

    NotificationEvent event(UUID eventId, String recipientLogin) {
        return new NotificationEvent(
                eventId,
                UUID.randomUUID(),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                recipientLogin,
                "Account replenished",
                Instant.now(),
                new BigDecimal("100.00"),
                Currency.RUB
        );
    }
}
