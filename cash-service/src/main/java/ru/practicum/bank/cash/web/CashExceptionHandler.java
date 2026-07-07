package ru.practicum.bank.cash.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.cash.client.AccountsClientException;
import ru.practicum.bank.cash.client.BlockerClientException;
import ru.practicum.bank.cash.client.ExchangeClientException;
import ru.practicum.bank.cash.dto.ApiErrorResponse;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.MissingPreferredUsernameException;
import ru.practicum.bank.cash.exception.OperationBlockedException;

@RestControllerAdvice
public class CashExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CashExceptionHandler.class);

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmount(InvalidAmountException exception) {
        log.warn("Cash request rejected status=bad_request errorCode=INVALID_AMOUNT source=cash-service");
        return new ApiErrorResponse("INVALID_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidAmountScaleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmountScale(InvalidAmountScaleException exception) {
        log.warn("Cash request rejected status=bad_request errorCode=INVALID_AMOUNT_SCALE source=cash-service");
        return new ApiErrorResponse("INVALID_AMOUNT_SCALE", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        log.warn("Cash request rejected status=bad_request errorCode=VALIDATION_ERROR source=cash-service");
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MissingPreferredUsernameException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleMissingPreferredUsername(MissingPreferredUsernameException exception) {
        log.warn("Cash request rejected status=unauthorized errorCode=UNAUTHORIZED source=cash-service");
        return new ApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(OperationBlockedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleOperationBlocked(OperationBlockedException exception) {
        log.warn("Cash request rejected status=unprocessable_entity errorCode=OPERATION_BLOCKED source=cash-service");
        return new ApiErrorResponse("OPERATION_BLOCKED", exception.getMessage());
    }

    @ExceptionHandler(AccountsClientException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountsClient(AccountsClientException exception) {
        if (exception.getStatusCode().is4xxClientError()) {
            log.warn(
                    "Cash downstream request rejected status={} errorCode={} source=cash-service targetService=accounts-service",
                    exception.getStatusCode().value(),
                    exception.getCode()
            );
            return ResponseEntity.status(exception.getStatusCode())
                    .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
        }

        log.error(
                "Cash downstream request failed status={} errorCode={} errorCategory=downstream_unavailable errorType={} source=cash-service targetService=accounts-service",
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
                "Cash downstream request failed status=502 errorCode=BLOCKER_SERVICE_UNAVAILABLE errorCategory=downstream_unavailable errorType={} source=cash-service targetService=blocker-service",
                exception.getClass().getSimpleName()
        );
        return new ApiErrorResponse("BLOCKER_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(ExchangeClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleExchangeClient(ExchangeClientException exception) {
        log.error(
                "Cash downstream request failed status=502 errorCode=EXCHANGE_SERVICE_UNAVAILABLE errorCategory=downstream_unavailable errorType={} source=cash-service targetService=exchange-service",
                exception.getClass().getSimpleName()
        );
        return new ApiErrorResponse("EXCHANGE_SERVICE_UNAVAILABLE", exception.getMessage());
    }
}
