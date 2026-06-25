package ru.practicum.bank.transfer.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.transfer.client.NotificationRequest;
import ru.practicum.bank.transfer.client.NotificationsClient;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final TransferExecutor transferExecutor;
    private final NotificationsClient notificationsClient;

    public TransferService(TransferExecutor transferExecutor, NotificationsClient notificationsClient) {
        this.transferExecutor = transferExecutor;
        this.notificationsClient = notificationsClient;
    }

    public TransferResponse transfer(String senderLogin, TransferRequest request) {
        validateAmount(request.amount());
        if (senderLogin.equals(request.recipientLogin())) {
            throw new SelfTransferForbiddenException();
        }

        var operationId = UUID.randomUUID().toString();
        var result = transferExecutor.execute(new TransferOperation(
                senderLogin,
                request.recipientLogin(),
                request.amount(),
                request.currency(),
                operationId
        ));
        notificationsClient.notify(new NotificationRequest(
                senderLogin,
                "TRANSFER_COMPLETED",
                "Transfer completed to " + request.recipientLogin() + ": " + request.amount() + " " + request.currency(),
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
}
