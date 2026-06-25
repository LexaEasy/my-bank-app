package ru.practicum.bank.cash.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record CashOperationResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal balance,
        String currency,
        String message
) {
}
