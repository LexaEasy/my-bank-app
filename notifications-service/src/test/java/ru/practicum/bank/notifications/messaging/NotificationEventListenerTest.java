package ru.practicum.bank.notifications.messaging;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;
import ru.practicum.bank.notifications.service.NotificationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class NotificationEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationEventListener listener = new NotificationEventListener(
            notificationService,
            Validation.buildDefaultValidatorFactory().getValidator()
    );

    @Test
    void shouldPassValidEventToServiceWithoutLoggingRecipient(CapturedOutput output) {
        var event = event("ivan");

        listener.listen(record(event));

        verify(notificationService).notify(event);
        org.assertj.core.api.Assertions.assertThat(output)
                .contains("eventId=" + event.eventId())
                .doesNotContain("ivan")
                .doesNotContain(event.message());
    }

    @Test
    void shouldRejectInvalidEvent() {
        var event = event(" ");

        assertThatThrownBy(() -> listener.listen(record(event)))
                .isInstanceOf(ConstraintViolationException.class);
        verify(notificationService, never()).notify(event);
    }

    @Test
    void shouldRejectMoneyEventWithoutAmountAndCurrency() {
        var event = new NotificationEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                "ivan",
                "Пополнение счёта",
                Instant.parse("2026-06-30T05:00:00Z"),
                null,
                null
        );

        assertThatThrownBy(() -> listener.listen(record(event)))
                .isInstanceOf(ConstraintViolationException.class);
        verify(notificationService, never()).notify(event);
    }

    private ConsumerRecord<String, NotificationEvent> record(NotificationEvent event) {
        return new ConsumerRecord<>("bank.notifications", 1, 42L, event.recipientLogin(), event);
    }

    private NotificationEvent event(String recipientLogin) {
        return new NotificationEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                recipientLogin,
                "Пополнение счёта",
                Instant.parse("2026-06-30T05:00:00Z"),
                new BigDecimal("100.00"),
                Currency.RUB
        );
    }
}
