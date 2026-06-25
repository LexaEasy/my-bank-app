package ru.practicum.bank.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank
        String recipientLogin,

        @NotNull
        BigDecimal amount,

        @NotNull
        Currency currency
) {
}
