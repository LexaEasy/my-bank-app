package ru.practicum.bank.notifications.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.bank.notifications.dto.NotificationRequest;
import ru.practicum.bank.notifications.dto.NotificationResponse;
import ru.practicum.bank.notifications.service.NotificationService;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/api/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse createNotification(@Valid @RequestBody NotificationRequest request) {
        notificationService.notify(request);

        return new NotificationResponse("ACCEPTED");
    }
}
