package ru.practicum.bank.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.notifications.dto.NotificationRequest;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void notify(NotificationEvent event) {
        log.info(
                "Notification accepted: eventId={}, operationId={}, source={}, type={}, recipientLogin={}, message={}",
                event.eventId(),
                event.operationId(),
                event.source(),
                event.type(),
                event.recipientLogin(),
                event.message()
        );
    }

    public void notify(NotificationRequest request) {
        log.info(
                "Notification accepted: recipientLogin={}, type={}, operationId={}, message={}",
                request.recipientLogin(),
                request.type(),
                request.operationId(),
                request.message()
        );
    }
}
