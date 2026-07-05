package ru.practicum.bank.notifications.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationDeliveryFailureRecovererTest {

    private final ConsumerRecordRecoverer delegate = mock(ConsumerRecordRecoverer.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final NotificationDeliveryFailureRecoverer recoverer =
            new NotificationDeliveryFailureRecoverer(delegate, meterRegistry, new ObjectMapper());

    @Test
    void shouldCountSuccessfulDltRecoveryOnce() {
        var record = new ConsumerRecord<String, NotificationEvent>(
                "bank.notifications",
                0,
                10L,
                "ivan",
                event("ivan")
        );
        var exception = new IllegalStateException("processing failed");

        recoverer.accept(record, exception);

        verify(delegate).accept(record, exception);
        assertThat(failureCount("ivan")).isEqualTo(1);
    }

    @Test
    void shouldUseUnknownForInvalidJsonWithoutLogin() {
        byte[] data = "{invalid-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var record = new ConsumerRecord<String, Object>("bank.notifications", 0, 10L, "key", null);
        var exception = new DeserializationException("invalid", data, false, new IllegalArgumentException());

        recoverer.accept(record, exception);

        assertThat(failureCount("unknown")).isEqualTo(1);
    }

    @Test
    void shouldNotCountWhenDltPublishingFails() {
        var record = new ConsumerRecord<String, NotificationEvent>(
                "bank.notifications",
                0,
                10L,
                "ivan",
                event("ivan")
        );
        var exception = new IllegalStateException("processing failed");
        doThrow(new IllegalStateException("DLT unavailable")).when(delegate).accept(record, exception);

        assertThatThrownBy(() -> recoverer.accept(record, exception))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failureCount("ivan")).isZero();
    }

    private double failureCount(String recipientLogin) {
        var counter = meterRegistry.find(NotificationDeliveryFailureRecoverer.METRIC_NAME)
                .tag("recipient_login", recipientLogin)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private NotificationEvent event(String recipientLogin) {
        return new NotificationEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                recipientLogin,
                "Notification",
                Instant.parse("2026-06-30T05:00:00Z"),
                new BigDecimal("100.00"),
                Currency.RUB
        );
    }
}
