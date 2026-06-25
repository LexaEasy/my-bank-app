package ru.practicum.bank.cash.client;

import java.math.BigDecimal;

public record AccountsBalanceResponse(
        String login,
        BigDecimal balance,
        String currency
) {
}
