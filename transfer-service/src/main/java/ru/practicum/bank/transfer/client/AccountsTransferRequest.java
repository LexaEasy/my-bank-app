package ru.practicum.bank.transfer.client;

import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record AccountsTransferRequest(
        String senderLogin,
        String recipientLogin,
        BigDecimal amount,
        Currency currency,
        BigDecimal recipientAmount,
        Currency recipientCurrency,
        String operationId
) {
    public AccountsTransferRequest(
            String senderLogin,
            String recipientLogin,
            BigDecimal amount,
            Currency currency,
            String operationId
    ) {
        this(senderLogin, recipientLogin, amount, currency, amount, currency, operationId);
    }
}
