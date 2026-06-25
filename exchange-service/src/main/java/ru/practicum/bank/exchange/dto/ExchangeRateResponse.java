package ru.practicum.bank.exchange.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResponse(
        Currency currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal buyRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal sellRate,
        Instant updatedAt
) {
}
