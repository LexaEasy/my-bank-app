package ru.practicum.bank.transfer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.model.OperationType;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;
import ru.practicum.bank.transfer.client.BlockerClient;
import ru.practicum.bank.transfer.client.ExchangeClient;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.OperationBlockedException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferExecutor transferExecutor;
    private final BlockerClient blockerClient;
    private final ExchangeClient exchangeClient;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public TransferService(
            TransferExecutor transferExecutor,
            BlockerClient blockerClient,
            ExchangeClient exchangeClient,
            NotificationEventPublisher notificationEventPublisher,
            Clock clock
    ) {
        this.transferExecutor = transferExecutor;
        this.blockerClient = blockerClient;
        this.exchangeClient = exchangeClient;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    public TransferResponse transfer(String senderLogin, TransferRequest request, UUID operationId) {
        validateAmount(request.amount(), operationId, request.currency());
        if (senderLogin.equals(request.recipientLogin())) {
            log.warn(
                    "Transfer operation rejected operationId={} operationType=TRANSFER currency={} status=rejected errorCode=SELF_TRANSFER_FORBIDDEN source=transfer-service",
                    operationId,
                    request.currency()
            );
            throw new SelfTransferForbiddenException();
        }

        var normalizedAmount = normalizeForBlocker(request);
        checkOperation(senderLogin, request, operationId, normalizedAmount);
        var conversion = convert(request);
        var result = transferExecutor.execute(new TransferOperation(
                senderLogin,
                request.recipientLogin(),
                request.amount(),
                request.currency(),
                conversion.targetAmount(),
                conversion.targetCurrency(),
                operationId.toString()
        ));
        publishNotifications(senderLogin, request, conversion, operationId);
        log.info(
                "Transfer operation completed operationId={} operationType=TRANSFER currency={} status=success source=transfer-service targetService=accounts-service",
                operationId,
                request.currency()
        );

        return new TransferResponse(
                result.senderLogin(),
                result.recipientLogin(),
                result.senderBalance(),
                result.currency(),
                "Transfer completed"
        );
    }

    private void validateAmount(BigDecimal amount, UUID operationId, Currency currency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(
                    "Transfer operation rejected operationId={} operationType=TRANSFER currency={} status=rejected errorCode=INVALID_AMOUNT source=transfer-service",
                    operationId,
                    currency
            );
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            log.warn(
                    "Transfer operation rejected operationId={} operationType=TRANSFER currency={} status=rejected errorCode=INVALID_AMOUNT_SCALE source=transfer-service",
                    operationId,
                    currency
            );
            throw new InvalidAmountScaleException();
        }
    }

    private void checkOperation(
            String senderLogin,
            TransferRequest request,
            UUID operationId,
            BigDecimal normalizedAmount
    ) {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Transfer blocker check prepared operationId={} operationType=TRANSFER currency={} source=transfer-service targetService=blocker-service",
                    operationId,
                    request.currency()
            );
        }
        var response = blockerClient.check(new OperationCheckRequest(
                operationId.toString(),
                OperationType.TRANSFER,
                null,
                senderLogin,
                request.recipientLogin(),
                request.amount(),
                request.currency(),
                normalizedAmount,
                Currency.RUB
        ));
        if (!response.allowed()) {
            log.warn(
                    "Transfer operation rejected operationId={} operationType=TRANSFER currency={} status=blocked errorCode=OPERATION_BLOCKED source=transfer-service targetService=blocker-service",
                    operationId,
                    request.currency()
            );
            throw new OperationBlockedException(response.reason());
        }
    }

    private BigDecimal normalizeForBlocker(TransferRequest request) {
        if (request.currency() == Currency.RUB) {
            return request.amount();
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Transfer amount normalization prepared operationType=EXCHANGE currency={} targetCurrency=RUB source=transfer-service targetService=exchange-service",
                    request.currency()
            );
        }
        ConversionResponse conversion = exchangeClient.convert(request.currency(), Currency.RUB, request.amount());
        return conversion.targetAmount();
    }

    private ConversionResponse convert(TransferRequest request) {
        if (request.currency() == request.resolvedTargetCurrency()) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Transfer currency conversion skipped operationType=TRANSFER currency={} targetCurrency={} source=transfer-service",
                        request.currency(),
                        request.resolvedTargetCurrency()
                );
            }
            return new ConversionResponse(
                    request.currency(),
                    request.currency(),
                    request.amount(),
                    request.amount(),
                    BigDecimal.ONE,
                    null
            );
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Transfer currency conversion prepared operationType=EXCHANGE currency={} targetCurrency={} source=transfer-service targetService=exchange-service",
                    request.currency(),
                    request.resolvedTargetCurrency()
            );
        }
        return exchangeClient.convert(request.currency(), request.resolvedTargetCurrency(), request.amount());
    }

    private void publishNotifications(
            String senderLogin,
            TransferRequest request,
            ConversionResponse conversion,
            UUID operationId
    ) {
        Instant occurredAt = Instant.now(clock);

        notificationEventPublisher.publish(new NotificationEvent(
                UUID.randomUUID(),
                operationId,
                NotificationSource.TRANSFER,
                NotificationType.TRANSFER_OUTGOING,
                senderLogin,
                "Перевод пользователю " + request.recipientLogin() + ": "
                        + conversion.sourceAmount() + " " + conversion.sourceCurrency(),
                occurredAt,
                conversion.sourceAmount(),
                conversion.sourceCurrency()
        ));
        notificationEventPublisher.publish(new NotificationEvent(
                UUID.randomUUID(),
                operationId,
                NotificationSource.TRANSFER,
                NotificationType.TRANSFER_INCOMING,
                request.recipientLogin(),
                "Получен перевод от " + senderLogin + ": "
                        + conversion.targetAmount() + " " + conversion.targetCurrency(),
                occurredAt,
                conversion.targetAmount(),
                conversion.targetCurrency()
        ));
    }
}
