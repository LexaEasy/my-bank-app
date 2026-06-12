package ru.practicum.bank.accounts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record TransferBalanceResponse(
        String senderLogin,
        String recipientLogin,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal senderBalance,

        String currency
) {
}
