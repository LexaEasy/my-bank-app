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
        Currency currency,

        Currency targetCurrency
) {
    public TransferRequest {
        if (targetCurrency == null) {
            targetCurrency = currency;
        }
    }

    public TransferRequest(String recipientLogin, BigDecimal amount, Currency currency) {
        this(recipientLogin, amount, currency, currency);
    }

    public Currency resolvedTargetCurrency() {
        return targetCurrency == null ? currency : targetCurrency;
    }
}
