package ru.practicum.bank.transfer.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.OperationType;
import ru.practicum.bank.transfer.client.BlockerClient;
import ru.practicum.bank.transfer.client.ExchangeClient;
import ru.practicum.bank.transfer.client.NotificationRequest;
import ru.practicum.bank.transfer.client.NotificationsClient;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.OperationBlockedException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final TransferExecutor transferExecutor;
    private final BlockerClient blockerClient;
    private final ExchangeClient exchangeClient;
    private final NotificationsClient notificationsClient;

    public TransferService(
            TransferExecutor transferExecutor,
            BlockerClient blockerClient,
            ExchangeClient exchangeClient,
            NotificationsClient notificationsClient
    ) {
        this.transferExecutor = transferExecutor;
        this.blockerClient = blockerClient;
        this.exchangeClient = exchangeClient;
        this.notificationsClient = notificationsClient;
    }

    public TransferResponse transfer(String senderLogin, TransferRequest request) {
        validateAmount(request.amount());
        if (senderLogin.equals(request.recipientLogin())) {
            throw new SelfTransferForbiddenException();
        }

        var operationId = UUID.randomUUID().toString();
        checkOperation(senderLogin, request, operationId);
        var conversion = convert(request);
        var result = transferExecutor.execute(new TransferOperation(
                senderLogin,
                request.recipientLogin(),
                request.amount(),
                request.currency(),
                conversion.targetAmount(),
                conversion.targetCurrency(),
                operationId
        ));
        notificationsClient.notify(new NotificationRequest(
                senderLogin,
                "TRANSFER_COMPLETED",
                notificationMessage(request, conversion),
                operationId
        ));

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

    private void checkOperation(String senderLogin, TransferRequest request, String operationId) {
        var response = blockerClient.check(new OperationCheckRequest(
                operationId,
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

    private String notificationMessage(TransferRequest request, ConversionResponse conversion) {
        if (conversion.sourceCurrency() == conversion.targetCurrency()) {
            return "Transfer completed to " + request.recipientLogin() + ": "
                    + request.amount() + " " + request.currency();
        }
        return "Transfer completed to " + request.recipientLogin() + ": "
                + conversion.sourceAmount() + " " + conversion.sourceCurrency()
                + " -> " + conversion.targetAmount() + " " + conversion.targetCurrency();
    }
}
