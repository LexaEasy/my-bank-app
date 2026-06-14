package ru.practicum.bank.transfer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record TransferResponse(
        String senderLogin,
        String recipientLogin,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal senderBalance,

        String currency,
        String message
) {
}
