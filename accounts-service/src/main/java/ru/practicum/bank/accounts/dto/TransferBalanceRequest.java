package ru.practicum.bank.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public record TransferBalanceRequest(
        @NotBlank
        String senderLogin,

        @NotBlank
        String recipientLogin,

        @NotNull
        BigDecimal amount,

        @NotNull
        Currency currency,

        BigDecimal recipientAmount,

        Currency recipientCurrency,

        @NotBlank
        String operationId
) {
    public TransferBalanceRequest(
            String senderLogin,
            String recipientLogin,
            BigDecimal amount,
            Currency currency,
            String operationId
    ) {
        this(senderLogin, recipientLogin, amount, currency, amount, currency, operationId);
    }

    public BigDecimal resolvedRecipientAmount() {
        return recipientAmount == null ? amount : recipientAmount;
    }

    public Currency resolvedRecipientCurrency() {
        return recipientCurrency == null ? currency : recipientCurrency;
    }
}
