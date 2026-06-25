package ru.practicum.bank.blocker.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
