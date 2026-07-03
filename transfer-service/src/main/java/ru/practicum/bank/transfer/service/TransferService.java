package ru.practicum.bank.transfer.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
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
        validateAmount(request.amount());
        if (senderLogin.equals(request.recipientLogin())) {
            throw new SelfTransferForbiddenException();
        }

        checkOperation(senderLogin, request, operationId);
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

        return new TransferResponse(
                result.senderLogin(),
                result.recipientLogin(),
                result.senderBalance(),
                result.currency(),
                "Transfer completed"
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountScaleException();
        }
    }

    private void checkOperation(String senderLogin, TransferRequest request, UUID operationId) {
        var response = blockerClient.check(new OperationCheckRequest(
                operationId.toString(),
                OperationType.TRANSFER,
                null,
                senderLogin,
                request.recipientLogin(),
                request.amount(),
                request.currency()
        ));
        if (!response.allowed()) {
            throw new OperationBlockedException(response.reason());
        }
    }

    private ConversionResponse convert(TransferRequest request) {
        if (request.currency() == request.resolvedTargetCurrency()) {
            return new ConversionResponse(
                    request.currency(),
                    request.currency(),
                    request.amount(),
                    request.amount(),
                    BigDecimal.ONE,
                    null
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
