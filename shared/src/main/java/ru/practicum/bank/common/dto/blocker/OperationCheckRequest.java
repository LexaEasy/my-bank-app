package ru.practicum.bank.common.dto.blocker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.model.OperationType;

import java.math.BigDecimal;

public record OperationCheckRequest(
        @NotBlank
        String operationId,

        @NotNull
        OperationType operationType,

        String login,

        String sender,

        String recipient,

        @NotNull
        @Positive
        BigDecimal amount,

        @NotNull
        Currency currency
) {
}
