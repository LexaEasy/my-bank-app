package ru.practicum.bank.common.dto.blocker;

public record OperationCheckResponse(
        boolean allowed,
        String reason
) {
}
