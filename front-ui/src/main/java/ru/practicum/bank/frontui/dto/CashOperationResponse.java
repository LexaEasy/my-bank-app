package ru.practicum.bank.frontui.dto;

import java.math.BigDecimal;

public record CashOperationResponse(
        BigDecimal balance,
        String currency,
        String message
) {
}
