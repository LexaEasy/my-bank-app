package ru.practicum.bank.frontui.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferForm(
        @NotBlank
        String recipientLogin,

        @NotNull
        BigDecimal amount,

        @NotBlank
        String currency
) {
}
