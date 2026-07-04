package ru.practicum.bank.transfer.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.KafkaNotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.transfer.client.BlockerClient;
import ru.practicum.bank.transfer.client.ExchangeClient;
import ru.practicum.bank.transfer.dto.TransferRequest;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class TransferKafkaFailureBusinessTest {

    private static final String TOPIC = "bank.notifications";

    @ParameterizedTest
    @EnumSource(KafkaFailureMode.class)
    void shouldKeepSuccessfulTransferWhenKafkaSendFails(KafkaFailureMode failureMode, CapturedOutput output) {
        var transferExecutor = mock(TransferExecutor.class);
        var blockerClient = mock(BlockerClient.class);
        var exchangeClient = mock(ExchangeClient.class);
        var kafkaTemplate = kafkaTemplate(failureMode);
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(transferExecutor.execute(any())).thenReturn(new TransferResult(
                "ivan",
                "petr",
                new BigDecimal("800.00"),
                "RUB"
        ));
        var service = new TransferService(
                transferExecutor,
                blockerClient,
                exchangeClient,
                new KafkaNotificationEventPublisher(kafkaTemplate, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), TOPIC),
                Clock.fixed(Instant.parse("2026-06-30T05:00:00Z"), ZoneOffset.UTC)
        );

        var response = service.transfer(
                "ivan",
                new TransferRequest("petr", new BigDecimal("200.00"), Currency.RUB),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
        );

        assertThat(response.senderBalance()).isEqualByComparingTo("800.00");
        assertThat(response.message()).isEqualTo("Transfer completed");
        verify(transferExecutor).execute(any());

        var events = verifyEvents(kafkaTemplate);
        assertThat(events).hasSize(2);
        assertFailureLogged(output, events.get(0));
        assertFailureLogged(output, events.get(1));
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate(KafkaFailureMode failureMode) {
        var kafkaTemplate = mock(KafkaTemplate.class);
        if (failureMode == KafkaFailureMode.SYNCHRONOUS) {
            when(kafkaTemplate.send(eq(TOPIC), any(String.class), any(NotificationEvent.class)))
                    .thenThrow(new IllegalStateException("kafka unavailable"));
        } else {
            var future = new CompletableFuture<SendResult<String, NotificationEvent>>();
            future.completeExceptionally(new IllegalStateException("kafka unavailable"));
            when(kafkaTemplate.send(eq(TOPIC), any(String.class), any(NotificationEvent.class)))
                    .thenReturn(future);
        }
        return kafkaTemplate;
    }

    private java.util.List<NotificationEvent> verifyEvents(
            KafkaTemplate<String, NotificationEvent> kafkaTemplate
    ) {
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(kafkaTemplate, times(2))
                .send(eq(TOPIC), any(String.class), eventCaptor.capture());
        return eventCaptor.getAllValues();
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
