package ru.practicum.bank.transfer.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
