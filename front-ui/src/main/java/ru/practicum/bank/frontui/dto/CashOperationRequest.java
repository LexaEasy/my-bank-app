package ru.practicum.bank.frontui.dto;

import java.math.BigDecimal;

public record CashOperationRequest(
        BigDecimal amount,
        String currency
) {
}
