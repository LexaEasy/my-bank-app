package ru.practicum.bank.cash.notification;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.bank.cash.client.AccountsBalanceResponse;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.service.CashService;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@EmbeddedKafka(
        kraft = true,
        partitions = 3,
        topics = CashNotificationKafkaIntegrationTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CashNotificationKafkaIntegrationTest {

    static final String TOPIC = "bank.notifications";

    @Autowired
    private CashService cashService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private AccountsClient accountsClient;

    @MockitoBean
    private BlockerClient blockerClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldPublishDepositNotification() {
        try (var consumer = consumer()) {
            when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
            when(accountsClient.deposit(any())).thenReturn(
                    new AccountsBalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
            );

            cashService.deposit("ivan", request("100.00"));

            var record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo(record.value().recipientLogin()).isEqualTo("ivan");
            assertThat(record.value().source()).isEqualTo(NotificationSource.CASH);
            assertThat(record.value().type()).isEqualTo(NotificationType.CASH_DEPOSITED);
            assertThat(record.value().amount()).isEqualByComparingTo("100.00");
            assertThat(record.value().currency()).isEqualTo(Currency.RUB);
        }
    }

    @Test
    void shouldNotPublishNotificationForInvalidDeposit() {
        try (var consumer = consumer()) {
            assertThatThrownBy(() -> cashService.deposit("ivan", request("0.00")))
                    .isInstanceOf(InvalidAmountException.class);

            assertThat(KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).count()).isZero();
        }
    }

    private Consumer<String, NotificationEvent> consumer() {
        var properties = KafkaTestUtils.consumerProps(
                "cash-notification-" + UUID.randomUUID(),
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

    private CashOperationRequest request(String amount) {
        return new CashOperationRequest(new BigDecimal(amount), Currency.RUB);
    }
}
