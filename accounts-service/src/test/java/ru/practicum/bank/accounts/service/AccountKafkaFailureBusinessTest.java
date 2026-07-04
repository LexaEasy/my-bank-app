package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.mapper.AccountMapper;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.accounts.notification.AccountProfileUpdatedEvent;
import ru.practicum.bank.accounts.notification.AccountProfileUpdatedNotificationListener;
import ru.practicum.bank.accounts.notification.AccountUpdatedNotificationFactory;
import ru.practicum.bank.accounts.repository.AccountRepository;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.KafkaNotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AccountKafkaFailureBusinessTest {

    private static final String TOPIC = "bank.notifications";

    @ParameterizedTest
    @EnumSource(KafkaFailureMode.class)
    void shouldKeepUpdatedProfileWhenKafkaSendFails(KafkaFailureMode failureMode, CapturedOutput output) {
        var repository = mock(AccountRepository.class);
        var applicationEvents = mock(ApplicationEventPublisher.class);
        var kafkaTemplate = kafkaTemplate(failureMode);
        var listener = new AccountProfileUpdatedNotificationListener(
                new KafkaNotificationEventPublisher(kafkaTemplate, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), TOPIC),
                new AccountUpdatedNotificationFactory()
        );
        doAnswer(invocation -> {
            listener.onProfileUpdated(invocation.getArgument(0, AccountProfileUpdatedEvent.class));
            return null;
        }).when(applicationEvents).publishEvent(any(AccountProfileUpdatedEvent.class));

        var account = new Account(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                Currency.RUB
        );
        when(repository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);
        var service = new AccountService(
                repository,
                new AccountMapper(),
                Clock.fixed(Instant.parse("2026-06-30T05:00:00Z"), ZoneOffset.UTC),
                applicationEvents
        );

        var response = service.updateCurrentAccount(
                "ivan",
                new UpdateAccountRequest("Иван Иванов", LocalDate.of(1992, 5, 10))
        );

        assertThat(response.name()).isEqualTo("Иван Иванов");
        assertThat(response.birthdate()).isEqualTo(LocalDate.of(1992, 5, 10));
        verify(repository).save(account);

        var event = verifyEvent(kafkaTemplate);
        assertFailureLogged(output, event);
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate(KafkaFailureMode failureMode) {
        var kafkaTemplate = mock(KafkaTemplate.class);
        if (failureMode == KafkaFailureMode.SYNCHRONOUS) {
            when(kafkaTemplate.send(eq(TOPIC), eq("ivan"), any(NotificationEvent.class)))
                    .thenThrow(new IllegalStateException("kafka unavailable"));
        } else {
            var future = new CompletableFuture<SendResult<String, NotificationEvent>>();
            future.completeExceptionally(new IllegalStateException("kafka unavailable"));
            when(kafkaTemplate.send(eq(TOPIC), eq("ivan"), any(NotificationEvent.class)))
                    .thenReturn(future);
        }
        return kafkaTemplate;
    }

    private NotificationEvent verifyEvent(KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("ivan"), eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private void assertFailureLogged(CapturedOutput output, NotificationEvent event) {
        assertThat(output)
                .contains("Notification event send failed")
                .contains("eventId=" + event.eventId())
                .contains("operationId=" + event.operationId())
                .contains("topic=" + TOPIC)
                .contains("errorType=IllegalStateException")
                .doesNotContain("kafka unavailable");
    }

    private enum KafkaFailureMode {
        SYNCHRONOUS,
        ASYNCHRONOUS
    }
}
