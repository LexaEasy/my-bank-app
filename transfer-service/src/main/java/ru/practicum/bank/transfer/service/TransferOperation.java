package ru.practicum.bank.transfer.service;

import ru.practicum.bank.transfer.model.Currency;

import java.math.BigDecimal;

public record TransferOperation(
        String senderLogin,
        String recipientLogin,
        BigDecimal amount,
        Currency currency,
        String operationId
) {
}
