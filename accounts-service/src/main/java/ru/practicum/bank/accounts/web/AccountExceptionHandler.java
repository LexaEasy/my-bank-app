package ru.practicum.bank.accounts.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.accounts.dto.ApiErrorResponse;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.exception.IdempotencyConflictException;
import ru.practicum.bank.accounts.exception.InsufficientFundsException;
import ru.practicum.bank.accounts.exception.InvalidAmountException;
import ru.practicum.bank.accounts.exception.InvalidAmountScaleException;
import ru.practicum.bank.accounts.exception.InvalidBirthdateException;
import ru.practicum.bank.accounts.exception.MissingPreferredUsernameException;
import ru.practicum.bank.accounts.exception.OperationAlreadyFailedException;
import ru.practicum.bank.accounts.exception.OperationInProgressException;
import ru.practicum.bank.accounts.exception.RecipientNotFoundException;
import ru.practicum.bank.accounts.exception.SelfTransferForbiddenException;

@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleAccountNotFound(AccountNotFoundException exception) {
        return new ApiErrorResponse("ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RecipientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleRecipientNotFound(RecipientNotFoundException exception) {
        return new ApiErrorResponse("RECIPIENT_NOT_FOUND", exception.getMessage());
    }

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

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleInsufficientFunds(InsufficientFundsException exception) {
        return new ApiErrorResponse("INSUFFICIENT_FUNDS", exception.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIdempotencyConflict(IdempotencyConflictException exception) {
        return new ApiErrorResponse("IDEMPOTENCY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(OperationInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleOperationInProgress(OperationInProgressException exception) {
        return new ApiErrorResponse("OPERATION_IN_PROGRESS", exception.getMessage());
    }

    @ExceptionHandler(OperationAlreadyFailedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleOperationAlreadyFailed(OperationAlreadyFailedException exception) {
        return new ApiErrorResponse("OPERATION_ALREADY_FAILED", exception.getMessage());
    }

    @ExceptionHandler({InvalidBirthdateException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(Exception exception) {
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MissingPreferredUsernameException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleMissingPreferredUsername(MissingPreferredUsernameException exception) {
        return new ApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }
}
