package ru.practicum.bank.exchange.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public record ConversionResponse(
        Currency sourceCurrency,
        Currency targetCurrency,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal sourceAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal targetAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal rate,
        Instant updatedAt
) {
}
