package ru.practicum.bank.accounts.notification;

import java.time.Instant;
import java.util.UUID;

public record AccountProfileUpdatedEvent(
        UUID operationId,
        String recipientLogin,
        Instant occurredAt
) {
}
