package ru.practicum.bank.transfer.client;

import java.math.BigDecimal;

public record AccountsTransferResponse(
        String senderLogin,
        String recipientLogin,
        BigDecimal senderBalance,
        String currency
) {
}
