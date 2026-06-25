package ru.practicum.bank.transfer.client;

import ru.practicum.bank.transfer.model.Currency;

import java.math.BigDecimal;

public record AccountsTransferRequest(
        String senderLogin,
        String recipientLogin,
        BigDecimal amount,
        Currency currency,
        String operationId
) {
}
