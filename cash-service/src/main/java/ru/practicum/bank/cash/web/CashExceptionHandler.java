package ru.practicum.bank.cash.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.cash.client.AccountsClientException;
import ru.practicum.bank.cash.client.BlockerClientException;
import ru.practicum.bank.cash.dto.ApiErrorResponse;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.MissingPreferredUsernameException;
import ru.practicum.bank.cash.exception.OperationBlockedException;

@RestControllerAdvice
public class CashExceptionHandler {

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

    @ExceptionHandler(OperationBlockedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleOperationBlocked(OperationBlockedException exception) {
        return new ApiErrorResponse("OPERATION_BLOCKED", exception.getMessage());
    }

    @ExceptionHandler(AccountsClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleAccountsClient(AccountsClientException exception) {
        return new ApiErrorResponse("ACCOUNTS_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(BlockerClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleBlockerClient(BlockerClientException exception) {
        return new ApiErrorResponse("BLOCKER_SERVICE_UNAVAILABLE", exception.getMessage());
    }
}
