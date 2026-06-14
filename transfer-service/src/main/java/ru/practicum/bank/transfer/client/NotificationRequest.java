package ru.practicum.bank.transfer.client;

public record NotificationRequest(
        String recipientLogin,
        String type,
        String message,
        String operationId
) {
}
