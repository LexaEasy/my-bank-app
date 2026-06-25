package ru.practicum.bank.cash.client;

public record NotificationRequest(
        String recipientLogin,
        String type,
        String message,
        String operationId
) {
}
