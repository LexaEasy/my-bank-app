package ru.practicum.bank.frontui.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
