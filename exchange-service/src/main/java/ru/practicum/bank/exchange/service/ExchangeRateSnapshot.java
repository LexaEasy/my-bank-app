package ru.practicum.bank.exchange.service;

import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

record ExchangeRateSnapshot(
        Currency currency,
        BigDecimal buyRate,
        BigDecimal sellRate,
        Instant updatedAt
) {
}
