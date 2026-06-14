package ru.practicum.bank.frontui.dto;

import java.math.BigDecimal;

public record TransferResponse(
        String senderLogin,
        String recipientLogin,
        BigDecimal senderBalance,
        String currency,
        String message
) {
}
