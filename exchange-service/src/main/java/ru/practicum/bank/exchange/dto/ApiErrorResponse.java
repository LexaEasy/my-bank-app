package ru.practicum.bank.exchange.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
