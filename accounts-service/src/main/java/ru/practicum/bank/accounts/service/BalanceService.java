package ru.practicum.bank.accounts.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;

@Service
public class BalanceService {

    private final IdempotencyService idempotencyService;
    private final BalanceOperationExecutor operationExecutor;

    public BalanceService(IdempotencyService idempotencyService, BalanceOperationExecutor operationExecutor) {
        this.idempotencyService = idempotencyService;
        this.operationExecutor = operationExecutor;
    }

    public BalanceResponse deposit(BalanceOperationRequest request) {
        return idempotencyService.execute(
                request.operationId(),
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> operationExecutor.deposit(request)
        );
    }

    public BalanceResponse withdraw(BalanceOperationRequest request) {
        return idempotencyService.execute(
                request.operationId(),
                "WITHDRAW",
                request,
                BalanceResponse.class,
                () -> operationExecutor.withdraw(request)
        );
    }

    public TransferBalanceResponse transfer(TransferBalanceRequest request) {
        return idempotencyService.execute(
                request.operationId(),
                "TRANSFER",
                request,
                TransferBalanceResponse.class,
                () -> operationExecutor.transfer(request)
        );
    }
}
