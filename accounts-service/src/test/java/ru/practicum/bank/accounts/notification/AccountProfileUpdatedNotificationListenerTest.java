package ru.practicum.bank.accounts.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountProfileUpdatedNotificationListenerTest {

    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final AccountProfileUpdatedNotificationListener listener =
            new AccountProfileUpdatedNotificationListener(
                    notificationEventPublisher,
                    new AccountUpdatedNotificationFactory()
            );

    @Test
    void shouldPublishAccountUpdatedNotification() {
        var operationId = UUID.fromString("3e3a3fec-843e-44e2-bcf5-3bea12845327");
        var occurredAt = Instant.parse("2026-06-30T05:00:00Z");
        var profileUpdatedEvent = new AccountProfileUpdatedEvent(operationId, "ivan", occurredAt);

        listener.onProfileUpdated(profileUpdatedEvent);

        var eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher).publish(eventCaptor.capture());

        var notificationEvent = eventCaptor.getValue();
        assertThat(notificationEvent.eventId()).isNotNull();
        assertThat(notificationEvent.operationId()).isEqualTo(operationId);
        assertThat(notificationEvent.source()).isEqualTo(NotificationSource.ACCOUNTS);
        assertThat(notificationEvent.type()).isEqualTo(NotificationType.ACCOUNT_UPDATED);
        assertThat(notificationEvent.recipientLogin()).isEqualTo("ivan");
        assertThat(notificationEvent.message()).isEqualTo(AccountUpdatedNotificationFactory.NOTIFICATION_MESSAGE);
        assertThat(notificationEvent.occurredAt()).isEqualTo(occurredAt);
        assertThat(notificationEvent.amount()).isNull();
        assertThat(notificationEvent.currency()).isNull();
    }
}
