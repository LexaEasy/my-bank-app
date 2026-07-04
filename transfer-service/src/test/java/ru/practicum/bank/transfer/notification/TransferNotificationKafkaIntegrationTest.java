package ru.practicum.bank.transfer.notification;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationType;
import ru.practicum.bank.transfer.client.BlockerClient;
import ru.practicum.bank.transfer.client.ExchangeClient;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.service.TransferExecutor;
import ru.practicum.bank.transfer.service.TransferResult;
import ru.practicum.bank.transfer.service.TransferService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.practicum.bank.common.notification.NotificationTopicsProperties.DEFAULT_PARTITION_COUNT;

@SpringBootTest(properties = "spring.kafka.admin.fail-fast=true")
@EmbeddedKafka(
        kraft = true,
        partitions = DEFAULT_PARTITION_COUNT,
        topics = TransferNotificationKafkaIntegrationTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransferNotificationKafkaIntegrationTest {

    static final String TOPIC = "bank.notifications";

    @Autowired
    private TransferService transferService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private TransferExecutor transferExecutor;

    @MockitoBean
    private BlockerClient blockerClient;

    @MockitoBean
    private ExchangeClient exchangeClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldPublishOutgoingAndIncomingNotifications() {
        try (var consumer = consumer()) {
            when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
            when(transferExecutor.execute(any())).thenReturn(
                    new TransferResult("ivan", "olga", new BigDecimal("800.00"), "RUB")
            );

            transferService.transfer("ivan", request("200.00"), UUID.randomUUID());

            var records = receive(consumer, 2);
            assertThat(records).hasSize(2);
            assertThat(records).allSatisfy(record ->
                    assertThat(record.key()).isEqualTo(record.value().recipientLogin())
            );
            assertThat(records).extracting(record -> record.value().type())
                    .containsExactlyInAnyOrder(
                            NotificationType.TRANSFER_OUTGOING,
                            NotificationType.TRANSFER_INCOMING
                    );
        }
    }

    @Test
    void shouldNotPublishNotificationsForInvalidTransfer() {
        try (var consumer = consumer()) {
            assertThatThrownBy(() -> transferService.transfer("ivan", request("0.00"), UUID.randomUUID()))
                    .isInstanceOf(InvalidAmountException.class);

            assertThat(receive(consumer, 1)).isEmpty();
        }
    }

    private List<ConsumerRecord<String, NotificationEvent>> receive(
            Consumer<String, NotificationEvent> consumer,
            int expected
    ) {
        var result = new ArrayList<ConsumerRecord<String, NotificationEvent>>();
        long deadline = System.nanoTime() + Duration.ofSeconds(expected == 1 ? 1 : 10).toNanos();
        while (result.size() < expected && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(250)).forEach(result::add);
        }
        return result;
    }

    private Consumer<String, NotificationEvent> consumer() {
        var properties = org.springframework.kafka.test.utils.KafkaTestUtils.consumerProps(
                "transfer-notification-" + UUID.randomUUID(),
                "true",
                embeddedKafka
        );
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumer = new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new JsonDeserializer<>(NotificationEvent.class, false)
        ).createConsumer();
        consumer.subscribe(List.of(TOPIC));
        consumer.poll(Duration.ofMillis(100));
        return consumer;
    }

    private TransferRequest request(String amount) {
        return new TransferRequest("olga", new BigDecimal(amount), Currency.RUB);
    }
}
