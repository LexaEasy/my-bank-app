package ru.practicum.bank.frontui.dto;

import java.math.BigDecimal;

public record TransferRequest(
        String recipientLogin,
        BigDecimal amount,
        String currency
) {
}
