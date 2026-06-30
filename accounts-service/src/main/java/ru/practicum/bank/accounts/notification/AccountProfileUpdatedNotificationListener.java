package ru.practicum.bank.accounts.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.util.UUID;

@Component
public class AccountProfileUpdatedNotificationListener {

    private static final String NOTIFICATION_MESSAGE = "Данные профиля обновлены";

    private final NotificationEventPublisher notificationEventPublisher;

    public AccountProfileUpdatedNotificationListener(NotificationEventPublisher notificationEventPublisher) {
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileUpdated(AccountProfileUpdatedEvent event) {
        notificationEventPublisher.publish(new NotificationEvent(
                UUID.randomUUID(),
                event.operationId(),
                NotificationSource.ACCOUNTS,
                NotificationType.ACCOUNT_UPDATED,
                event.recipientLogin(),
                NOTIFICATION_MESSAGE,
                event.occurredAt(),
                null,
                null
        ));
    }
}
