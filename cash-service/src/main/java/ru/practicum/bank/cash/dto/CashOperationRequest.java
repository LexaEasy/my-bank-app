package ru.practicum.bank.cash.dto;

import jakarta.validation.constraints.NotNull;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record CashOperationRequest(
        @NotNull
        BigDecimal amount,

        @NotNull
        Currency currency
) {
}
