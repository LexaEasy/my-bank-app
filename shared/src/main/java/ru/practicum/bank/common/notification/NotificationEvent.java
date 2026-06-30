package ru.practicum.bank.common.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NotificationEvent(
        @NotNull
        UUID eventId,

        @NotNull
        UUID operationId,

        @NotNull
        NotificationSource source,

        @NotNull
        NotificationType type,

        @NotBlank
        String recipientLogin,

        @NotBlank
        String message,

        @NotNull
        Instant occurredAt,

        @Positive
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount,

        Currency currency
) {
}
