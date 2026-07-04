package ru.practicum.bank.accounts.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.practicum.bank.common.notification.NotificationEventPublisher;

@Component
public class AccountProfileUpdatedNotificationListener {

    private final NotificationEventPublisher notificationEventPublisher;
    private final AccountUpdatedNotificationFactory notificationFactory;

    public AccountProfileUpdatedNotificationListener(
            NotificationEventPublisher notificationEventPublisher,
            AccountUpdatedNotificationFactory notificationFactory
    ) {
        this.notificationEventPublisher = notificationEventPublisher;
        this.notificationFactory = notificationFactory;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileUpdated(AccountProfileUpdatedEvent event) {
        notificationEventPublisher.publish(notificationFactory.create(event));
    }
}
