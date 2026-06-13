package ru.practicum.bank.notifications.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
