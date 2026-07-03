package ru.practicum.bank.accounts.notification;

import org.springframework.stereotype.Component;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

@Component
public class AccountUpdatedNotificationFactory {

    public static final String NOTIFICATION_MESSAGE = "Данные профиля обновлены";

    public NotificationEvent create(AccountProfileUpdatedEvent event) {
        return create(UUID.randomUUID(), event.operationId(), event.recipientLogin(), event.occurredAt());
    }

    public NotificationEvent create(
            UUID eventId,
            UUID operationId,
            String recipientLogin,
            Instant occurredAt
    ) {
        return new NotificationEvent(
                eventId,
                operationId,
                NotificationSource.ACCOUNTS,
                NotificationType.ACCOUNT_UPDATED,
                recipientLogin,
                NOTIFICATION_MESSAGE,
                occurredAt,
                null,
                null
        );
    }
}
