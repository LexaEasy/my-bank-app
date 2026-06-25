package ru.practicum.bank.cash.client;

import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record AccountsBalanceOperationRequest(
        String login,
        BigDecimal amount,
        Currency currency,
        String operationId
) {
}
