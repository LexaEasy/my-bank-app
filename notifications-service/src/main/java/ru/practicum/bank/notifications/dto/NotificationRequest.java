package ru.practicum.bank.notifications.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationRequest(
        @NotBlank
        String recipientLogin,

        @NotBlank
        String type,

        @NotBlank
        String message,

        @NotBlank
        String operationId
) {
}
