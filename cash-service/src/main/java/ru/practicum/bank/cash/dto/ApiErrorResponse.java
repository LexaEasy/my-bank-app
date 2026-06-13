package ru.practicum.bank.cash.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
