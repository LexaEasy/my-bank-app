package ru.practicum.bank.frontui.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CashForm(
        @NotNull
        @Positive
        BigDecimal amount,

        @NotNull
        String currency
) {
}
