package ru.practicum.bank.cash.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.practicum.bank.cash.client.AccountsBalanceResponse;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.KafkaNotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class CashKafkaFailureBusinessTest {

    private static final String TOPIC = "bank.notifications";

    @ParameterizedTest
    @EnumSource(KafkaFailureMode.class)
    void shouldKeepSuccessfulDepositWhenKafkaSendFails(KafkaFailureMode failureMode, CapturedOutput output) {
        var accountsClient = mock(AccountsClient.class);
        var blockerClient = mock(BlockerClient.class);
        var kafkaTemplate = kafkaTemplate(failureMode);
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.deposit(any())).thenReturn(new AccountsBalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "RUB"
        ));
        var service = new CashService(
                accountsClient,
                blockerClient,
                new KafkaNotificationEventPublisher(kafkaTemplate, TOPIC),
                Clock.fixed(Instant.parse("2026-06-30T05:00:00Z"), ZoneOffset.UTC)
        );

        var response = service.deposit(
                "ivan",
                new CashOperationRequest(new BigDecimal("250.00"), Currency.RUB),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        );

        assertThat(response.balance()).isEqualByComparingTo("1250.00");
        assertThat(response.message()).isEqualTo("Счёт пополнен");
        verify(accountsClient).deposit(any());

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
                .contains("reason=kafka unavailable");
    }

    private enum KafkaFailureMode {
        SYNCHRONOUS,
        ASYNCHRONOUS
    }
}
