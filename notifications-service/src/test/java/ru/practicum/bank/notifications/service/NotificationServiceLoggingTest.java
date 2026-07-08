package ru.practicum.bank.notifications.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceLoggingTest {

    private final NotificationService service = new NotificationService();

    @Test
    void shouldLogOnlySafeNotificationMetadata(CapturedOutput output) {
        var event = new NotificationEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                "private-login",
                "Private notification message",
                Instant.parse("2026-06-30T05:00:00Z"),
                new BigDecimal("100.00"),
                Currency.RUB
        );

        service.notify(event);

        assertThat(output)
                .contains("eventId=" + event.eventId())
                .contains("operationId=" + event.operationId())
                .contains("source=CASH")
                .contains("type=CASH_DEPOSITED")
                .doesNotContain("private-login")
                .doesNotContain("Private notification message");
    }
}
