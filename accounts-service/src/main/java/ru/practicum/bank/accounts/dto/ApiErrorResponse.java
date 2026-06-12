package ru.practicum.bank.accounts.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
