package ru.practicum.bank.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record BalanceOperationRequest(
        @NotBlank
        String login,

        @NotNull
        BigDecimal amount,

        @NotNull
        Currency currency,

        @NotBlank
        String operationId
) {
}
