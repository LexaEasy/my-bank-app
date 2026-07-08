package ru.practicum.bank.cash.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.practicum.bank.cash.client.AccountsBalanceOperationRequest;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.client.ExchangeClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.OperationBlockedException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.Currency;
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

    private static final Logger log = LoggerFactory.getLogger(CashService.class);

    private final AccountsClient accountsClient;
    private final BlockerClient blockerClient;
    private final ExchangeClient exchangeClient;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public CashService(
            AccountsClient accountsClient,
            BlockerClient blockerClient,
            ExchangeClient exchangeClient,
            NotificationEventPublisher notificationEventPublisher,
            Clock clock
    ) {
        this.accountsClient = accountsClient;
        this.blockerClient = blockerClient;
        this.exchangeClient = exchangeClient;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    public CashOperationResponse deposit(String login, CashOperationRequest request, UUID operationId) {
        validateAmount(request.amount(), operationId, OperationType.DEPOSIT, request.currency());
        checkOperation(login, request, operationId, OperationType.DEPOSIT);
        var balance = accountsClient.deposit(toAccountsRequest(login, request, operationId));
        publishNotification(login, request, operationId, NotificationType.CASH_DEPOSITED);
        log.info(
                "Cash operation completed operationId={} operationType=DEPOSIT currency={} status=success source=cash-service targetService=accounts-service",
                operationId,
                request.currency()
        );

        return new CashOperationResponse(balance.balance(), balance.currency(), "Счёт пополнен");
    }

    public CashOperationResponse withdraw(String login, CashOperationRequest request, UUID operationId) {
        validateAmount(request.amount(), operationId, OperationType.WITHDRAW, request.currency());
        checkOperation(login, request, operationId, OperationType.WITHDRAW);
        var balance = accountsClient.withdraw(toAccountsRequest(login, request, operationId));
        publishNotification(login, request, operationId, NotificationType.CASH_WITHDRAWN);
        log.info(
                "Cash operation completed operationId={} operationType=WITHDRAW currency={} status=success source=cash-service targetService=accounts-service",
                operationId,
                request.currency()
        );

        return new CashOperationResponse(balance.balance(), balance.currency(), "Деньги сняты со счёта");
    }

    private void validateAmount(BigDecimal amount, UUID operationId, OperationType operationType, Currency currency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(
                    "Cash operation rejected operationId={} operationType={} currency={} status=rejected errorCode=INVALID_AMOUNT source=cash-service",
                    operationId,
                    operationType,
                    currency
            );
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            log.warn(
                    "Cash operation rejected operationId={} operationType={} currency={} status=rejected errorCode=INVALID_AMOUNT_SCALE source=cash-service",
                    operationId,
                    operationType,
                    currency
            );
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
        var normalizedAmount = normalizeForBlocker(request);
        if (log.isDebugEnabled()) {
            log.debug(
                    "Cash blocker check prepared operationId={} operationType={} currency={} source=cash-service targetService=blocker-service",
                    operationId,
                    operationType,
                    request.currency()
            );
        }
        var response = blockerClient.check(new OperationCheckRequest(
                operationId.toString(),
                operationType,
                login,
                null,
                null,
                request.amount(),
                request.currency(),
                normalizedAmount,
                Currency.RUB
        ));
        if (!response.allowed()) {
            log.warn(
                    "Cash operation rejected operationId={} operationType={} currency={} status=blocked errorCode=OPERATION_BLOCKED source=cash-service targetService=blocker-service",
                    operationId,
                    operationType,
                    request.currency()
            );
            throw new OperationBlockedException(response.reason());
        }
    }

    private BigDecimal normalizeForBlocker(CashOperationRequest request) {
        if (request.currency() == Currency.RUB) {
            return request.amount();
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Cash amount normalization prepared operationType=EXCHANGE currency={} targetCurrency=RUB source=cash-service targetService=exchange-service",
                    request.currency()
            );
        }
        ConversionResponse conversion = exchangeClient.convert(request.currency(), Currency.RUB, request.amount());
        return conversion.targetAmount();
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
