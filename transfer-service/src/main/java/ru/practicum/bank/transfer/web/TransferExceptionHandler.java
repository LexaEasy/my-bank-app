package ru.practicum.bank.transfer.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.transfer.client.AccountsClientException;
import ru.practicum.bank.transfer.client.BlockerClientException;
import ru.practicum.bank.transfer.client.ExchangeClientException;
import ru.practicum.bank.transfer.dto.ApiErrorResponse;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.MissingPreferredUsernameException;
import ru.practicum.bank.transfer.exception.OperationBlockedException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;

@RestControllerAdvice
public class TransferExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferExceptionHandler.class);

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmount(InvalidAmountException exception) {
        log.warn("Transfer request rejected status=bad_request errorCode=INVALID_AMOUNT source=transfer-service");
        return new ApiErrorResponse("INVALID_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidAmountScaleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmountScale(InvalidAmountScaleException exception) {
        log.warn("Transfer request rejected status=bad_request errorCode=INVALID_AMOUNT_SCALE source=transfer-service");
        return new ApiErrorResponse("INVALID_AMOUNT_SCALE", exception.getMessage());
    }

    @ExceptionHandler(SelfTransferForbiddenException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleSelfTransfer(SelfTransferForbiddenException exception) {
        log.warn("Transfer request rejected status=unprocessable_entity errorCode=SELF_TRANSFER_FORBIDDEN source=transfer-service");
        return new ApiErrorResponse("SELF_TRANSFER_FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(OperationBlockedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleOperationBlocked(OperationBlockedException exception) {
        log.warn("Transfer request rejected status=unprocessable_entity errorCode=OPERATION_BLOCKED source=transfer-service");
        return new ApiErrorResponse("OPERATION_BLOCKED", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        log.warn("Transfer request rejected status=bad_request errorCode=VALIDATION_ERROR source=transfer-service");
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MissingPreferredUsernameException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleMissingPreferredUsername(MissingPreferredUsernameException exception) {
        log.warn("Transfer request rejected status=unauthorized errorCode=UNAUTHORIZED source=transfer-service");
        return new ApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(AccountsClientException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountsClient(AccountsClientException exception) {
        if (exception.getStatusCode().is4xxClientError()) {
            log.warn(
                    "Transfer downstream request rejected status={} errorCode={} source=transfer-service targetService=accounts-service",
                    exception.getStatusCode().value(),
                    exception.getCode()
            );
            return ResponseEntity.status(exception.getStatusCode())
                    .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
        }

        log.error(
                "Transfer downstream request failed status={} errorCode={} errorCategory=downstream_unavailable errorType={} source=transfer-service targetService=accounts-service",
                exception.getStatusCode().value(),
                exception.getCode(),
                exception.getClass().getSimpleName()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("ACCOUNTS_SERVICE_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(BlockerClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleBlockerClient(BlockerClientException exception) {
        log.error(
                "Transfer downstream request failed status=502 errorCode=BLOCKER_SERVICE_UNAVAILABLE errorCategory=downstream_unavailable errorType={} source=transfer-service targetService=blocker-service",
                exception.getClass().getSimpleName()
        );
        return new ApiErrorResponse("BLOCKER_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(ExchangeClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleExchangeClient(ExchangeClientException exception) {
        log.error(
                "Transfer downstream request failed status=502 errorCode=EXCHANGE_SERVICE_UNAVAILABLE errorCategory=downstream_unavailable errorType={} source=transfer-service targetService=exchange-service",
                exception.getClass().getSimpleName()
        );
        return new ApiErrorResponse("EXCHANGE_SERVICE_UNAVAILABLE", exception.getMessage());
    }
}
