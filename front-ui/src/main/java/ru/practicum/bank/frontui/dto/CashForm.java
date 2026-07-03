package ru.practicum.bank.frontui.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CashForm(
        @NotNull
        @Positive
        BigDecimal amount,

        @NotNull
        String currency,

        @NotBlank
        String idempotencyKey
) {
    public CashForm(BigDecimal amount, String currency) {
        this(amount, currency, UUID.randomUUID().toString());
    }
}
