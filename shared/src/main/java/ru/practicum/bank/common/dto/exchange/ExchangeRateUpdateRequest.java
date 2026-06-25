package ru.practicum.bank.common.dto.exchange;

import jakarta.validation.constraints.NotNull;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record ExchangeRateUpdateRequest(
        @NotNull
        Currency currency,

        @NotNull
        BigDecimal buyRate,

        @NotNull
        BigDecimal sellRate
) {
}
