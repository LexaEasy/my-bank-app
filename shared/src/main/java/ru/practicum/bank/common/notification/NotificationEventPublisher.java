package ru.practicum.bank.common.notification;

public interface NotificationEventPublisher {

    void publish(NotificationEvent event);
}
