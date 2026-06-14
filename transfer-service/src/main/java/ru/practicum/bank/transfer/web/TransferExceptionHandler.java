package ru.practicum.bank.transfer.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.transfer.client.AccountsClientException;
import ru.practicum.bank.transfer.client.NotificationsClientException;
import ru.practicum.bank.transfer.dto.ApiErrorResponse;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.MissingPreferredUsernameException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;

@RestControllerAdvice
public class TransferExceptionHandler {

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmount(InvalidAmountException exception) {
        return new ApiErrorResponse("INVALID_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidAmountScaleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmountScale(InvalidAmountScaleException exception) {
        return new ApiErrorResponse("INVALID_AMOUNT_SCALE", exception.getMessage());
    }

    @ExceptionHandler(SelfTransferForbiddenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleSelfTransfer(SelfTransferForbiddenException exception) {
        return new ApiErrorResponse("SELF_TRANSFER_FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MissingPreferredUsernameException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleMissingPreferredUsername(MissingPreferredUsernameException exception) {
        return new ApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(AccountsClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleAccountsClient(AccountsClientException exception) {
        return new ApiErrorResponse("ACCOUNTS_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(NotificationsClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleNotificationsClient(NotificationsClientException exception) {
        return new ApiErrorResponse("NOTIFICATIONS_SERVICE_UNAVAILABLE", exception.getMessage());
    }
}
