package ru.practicum.bank.blocker.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.blocker.config.BlockerProperties;
import ru.practicum.bank.blocker.exception.InvalidOperationRequestException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.OperationType;

@Service
public class BlockerService {

    private final BlockerProperties properties;

    public BlockerService(BlockerProperties properties) {
        this.properties = properties;
    }

    public OperationCheckResponse check(OperationCheckRequest request) {
        validateParticipants(request);
        if (request.amount().compareTo(properties.maxAmount()) > 0) {
            return new OperationCheckResponse(false, "Operation amount exceeds blocker limit");
        }

        return new OperationCheckResponse(true, null);
    }

    private void validateParticipants(OperationCheckRequest request) {
        if (request.operationType() == OperationType.TRANSFER) {
            requireText(request.sender(), "sender is required for transfer");
            requireText(request.recipient(), "recipient is required for transfer");
            return;
        }

        requireText(request.login(), "login is required for cash operation");
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidOperationRequestException(message);
        }
    }
}
