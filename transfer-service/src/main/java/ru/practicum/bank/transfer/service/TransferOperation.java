package ru.practicum.bank.transfer.service;

import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record TransferOperation(
        String senderLogin,
        String recipientLogin,
        BigDecimal amount,
        Currency currency,
        BigDecimal recipientAmount,
        Currency recipientCurrency,
        String operationId
) {
    public TransferOperation(
            String senderLogin,
            String recipientLogin,
            BigDecimal amount,
            Currency currency,
            String operationId
    ) {
        this(senderLogin, recipientLogin, amount, currency, amount, currency, operationId);
    }
}
