package ru.practicum.bank.accounts.notification;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.exception.InvalidBirthdateException;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.accounts.repository.AccountRepository;
import ru.practicum.bank.accounts.service.AccountService;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.kafka.admin.fail-fast=true")
@EmbeddedKafka(
        kraft = true,
        partitions = 3,
        topics = AccountProfileNotificationIntegrationTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountProfileNotificationIntegrationTest {

    static final String TOPIC = "bank.notifications";

    @Autowired
    private AccountService accountService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldPublishNotificationAfterSuccessfulTransactionCommit() {
        try (var consumer = consumer()) {
            var account = account();
            when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
            when(accountRepository.save(account)).thenReturn(account);

            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    accountService.updateCurrentAccount(
                            "ivan",
                            new UpdateAccountRequest("Иван Иванов", LocalDate.of(1992, 5, 10))
                    )
            );

            var record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo("ivan");
            assertThat(record.value().recipientLogin()).isEqualTo("ivan");
            assertThat(record.value().source()).isEqualTo(NotificationSource.ACCOUNTS);
            assertThat(record.value().type()).isEqualTo(NotificationType.ACCOUNT_UPDATED);
        }
    }

    @Test
    void shouldNotPublishNotificationAfterInvalidUpdate() {
        try (var consumer = consumer()) {
            assertThatThrownBy(() -> accountService.updateCurrentAccount(
                    "ivan",
                    new UpdateAccountRequest("Иван Иванов", LocalDate.now().minusYears(10))
            )).isInstanceOf(InvalidBirthdateException.class);

            assertThat(KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).count()).isZero();
        }
    }

    private Consumer<String, NotificationEvent> consumer() {
        var properties = KafkaTestUtils.consumerProps(
                "accounts-notification-" + UUID.randomUUID(),
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

    private Account account() {
        return new Account(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                Currency.RUB
        );
    }
}
