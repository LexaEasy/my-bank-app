package ru.practicum.bank.cash.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.cash.client.AccountsBalanceOperationRequest;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.OperationBlockedException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.model.OperationType;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CashService {

    private final AccountsClient accountsClient;
    private final BlockerClient blockerClient;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public CashService(
            AccountsClient accountsClient,
            BlockerClient blockerClient,
            NotificationEventPublisher notificationEventPublisher,
            Clock clock
    ) {
        this.accountsClient = accountsClient;
        this.blockerClient = blockerClient;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    public CashOperationResponse deposit(String login, CashOperationRequest request, UUID operationId) {
        validateAmount(request.amount());
        checkOperation(login, request, operationId, OperationType.DEPOSIT);
        var balance = accountsClient.deposit(toAccountsRequest(login, request, operationId));
        publishNotification(login, request, operationId, NotificationType.CASH_DEPOSITED);

        return new CashOperationResponse(balance.balance(), balance.currency(), "Счёт пополнен");
    }

    public CashOperationResponse withdraw(String login, CashOperationRequest request, UUID operationId) {
        validateAmount(request.amount());
        checkOperation(login, request, operationId, OperationType.WITHDRAW);
        var balance = accountsClient.withdraw(toAccountsRequest(login, request, operationId));
        publishNotification(login, request, operationId, NotificationType.CASH_WITHDRAWN);

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
            UUID operationId
    ) {
        return new AccountsBalanceOperationRequest(
                login,
                request.amount(),
                request.currency(),
                operationId.toString()
        );
    }

    private void checkOperation(
            String login,
            CashOperationRequest request,
            UUID operationId,
            OperationType operationType
    ) {
        var response = blockerClient.check(new OperationCheckRequest(
                operationId.toString(),
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

    private void publishNotification(
            String login,
            CashOperationRequest request,
            UUID operationId,
            NotificationType type
    ) {
        String message = type == NotificationType.CASH_DEPOSITED
                ? "Счёт пополнен на " + request.amount() + " " + request.currency()
                : "Со счёта снято " + request.amount() + " " + request.currency();

        notificationEventPublisher.publish(new NotificationEvent(
                UUID.randomUUID(),
                operationId,
                NotificationSource.CASH,
                type,
                login,
                message,
                Instant.now(clock),
                request.amount(),
                request.currency()
        ));
    }
}
