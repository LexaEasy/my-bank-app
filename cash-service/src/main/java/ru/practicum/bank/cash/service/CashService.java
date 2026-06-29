package ru.practicum.bank.cash.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.cash.client.AccountsBalanceOperationRequest;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.client.NotificationRequest;
import ru.practicum.bank.cash.client.NotificationsClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.OperationBlockedException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.model.OperationType;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CashService {

    private final AccountsClient accountsClient;
    private final BlockerClient blockerClient;
    private final NotificationsClient notificationsClient;

    public CashService(AccountsClient accountsClient, BlockerClient blockerClient, NotificationsClient notificationsClient) {
        this.accountsClient = accountsClient;
        this.blockerClient = blockerClient;
        this.notificationsClient = notificationsClient;
    }

    public CashOperationResponse deposit(String login, CashOperationRequest request) {
        validateAmount(request.amount());
        var operationId = UUID.randomUUID().toString();
        checkOperation(login, request, operationId, OperationType.DEPOSIT);
        var balance = accountsClient.deposit(toAccountsRequest(login, request, operationId));
        notificationsClient.notify(new NotificationRequest(
                login,
                "CASH_DEPOSIT",
                "Счёт пополнен на " + request.amount() + " " + request.currency(),
                operationId
        ));

        return new CashOperationResponse(balance.balance(), balance.currency(), "Счёт пополнен");
    }

    public CashOperationResponse withdraw(String login, CashOperationRequest request) {
        validateAmount(request.amount());
        var operationId = UUID.randomUUID().toString();
        checkOperation(login, request, operationId, OperationType.WITHDRAW);
        var balance = accountsClient.withdraw(toAccountsRequest(login, request, operationId));
        notificationsClient.notify(new NotificationRequest(
                login,
                "CASH_WITHDRAW",
                "Со счёта снято " + request.amount() + " " + request.currency(),
                operationId
        ));

        return new CashOperationResponse(balance.balance(), balance.currency(), "Деньги сняты со счёта");
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountScaleException();
        }
    }

    private AccountsBalanceOperationRequest toAccountsRequest(
            String login,
            CashOperationRequest request,
            String operationId
    ) {
        return new AccountsBalanceOperationRequest(
                login,
                request.amount(),
                request.currency(),
                operationId
        );
    }

    private void checkOperation(
            String login,
            CashOperationRequest request,
            String operationId,
            OperationType operationType
    ) {
        var response = blockerClient.check(new OperationCheckRequest(
                operationId,
                operationType,
                login,
                null,
                null,
                request.amount(),
                request.currency()
        ));
        if (!response.allowed()) {
            throw new OperationBlockedException(response.reason());
        }
    }
}
