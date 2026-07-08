package ru.practicum.bank.accounts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final IdempotencyService idempotencyService;
    private final BalanceOperationExecutor operationExecutor;

    public BalanceService(IdempotencyService idempotencyService, BalanceOperationExecutor operationExecutor) {
        this.idempotencyService = idempotencyService;
        this.operationExecutor = operationExecutor;
    }

    public BalanceResponse deposit(BalanceOperationRequest request) {
        BalanceResponse response = idempotencyService.execute(
                request.operationId(),
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> operationExecutor.deposit(request)
        );
        log.info(
                "Balance operation completed operationId={} operationType=DEPOSIT currency={} status=success source=accounts-service",
                request.operationId(),
                request.currency()
        );
        return response;
    }

    public BalanceResponse withdraw(BalanceOperationRequest request) {
        BalanceResponse response = idempotencyService.execute(
                request.operationId(),
                "WITHDRAW",
                request,
                BalanceResponse.class,
                () -> operationExecutor.withdraw(request)
        );
        log.info(
                "Balance operation completed operationId={} operationType=WITHDRAW currency={} status=success source=accounts-service",
                request.operationId(),
                request.currency()
        );
        return response;
    }

    public TransferBalanceResponse transfer(TransferBalanceRequest request) {
        TransferBalanceResponse response = idempotencyService.execute(
                request.operationId(),
                "TRANSFER",
                request,
                TransferBalanceResponse.class,
                () -> operationExecutor.transfer(request)
        );
        log.info(
                "Balance operation completed operationId={} operationType=TRANSFER currency={} status=success source=accounts-service",
                request.operationId(),
                request.currency()
        );
        return response;
    }
}
