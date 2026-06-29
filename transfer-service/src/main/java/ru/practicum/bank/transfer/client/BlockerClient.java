package ru.practicum.bank.transfer.client;

import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;

public interface BlockerClient {

    OperationCheckResponse check(OperationCheckRequest request);
}
