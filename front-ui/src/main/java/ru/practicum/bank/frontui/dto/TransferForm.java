package ru.practicum.bank.frontui.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferForm(
        @NotBlank
        String recipientLogin,

        @NotNull
        BigDecimal amount,

        @NotBlank
        String currency,

        @NotBlank
        String sourceCurrency,

        @NotBlank
        String idempotencyKey
) {
    public TransferForm(String recipientLogin, BigDecimal amount, String currency) {
        this(recipientLogin, amount, currency, "RUB", UUID.randomUUID().toString());
    }

    public TransferForm(String recipientLogin, BigDecimal amount, String currency, String sourceCurrency) {
        this(recipientLogin, amount, currency, sourceCurrency, UUID.randomUUID().toString());
    }
}
