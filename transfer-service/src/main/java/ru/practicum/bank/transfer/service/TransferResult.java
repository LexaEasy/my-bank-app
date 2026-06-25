package ru.practicum.bank.transfer.service;

import java.math.BigDecimal;

public record TransferResult(
        String senderLogin,
        String recipientLogin,
        BigDecimal senderBalance,
        String currency
) {
}
