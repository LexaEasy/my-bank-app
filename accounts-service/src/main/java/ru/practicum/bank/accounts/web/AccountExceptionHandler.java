package ru.practicum.bank.accounts.web;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.accounts.dto.ApiErrorResponse;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.exception.CurrencyMismatchException;
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

    private static final Logger log = LoggerFactory.getLogger(AccountExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleAccountNotFound(AccountNotFoundException exception) {
        log.warn("Account request rejected status=not_found errorCode=ACCOUNT_NOT_FOUND source=accounts-service");
        return new ApiErrorResponse("ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RecipientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleRecipientNotFound(RecipientNotFoundException exception) {
        log.warn("Account request rejected status=not_found errorCode=RECIPIENT_NOT_FOUND source=accounts-service");
        return new ApiErrorResponse("RECIPIENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmount(InvalidAmountException exception) {
        log.warn("Account request rejected status=bad_request errorCode=INVALID_AMOUNT source=accounts-service");
        return new ApiErrorResponse("INVALID_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidAmountScaleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmountScale(InvalidAmountScaleException exception) {
        log.warn("Account request rejected status=bad_request errorCode=INVALID_AMOUNT_SCALE source=accounts-service");
        return new ApiErrorResponse("INVALID_AMOUNT_SCALE", exception.getMessage());
    }

    @ExceptionHandler(SelfTransferForbiddenException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleSelfTransfer(SelfTransferForbiddenException exception) {
        log.warn("Account request rejected status=unprocessable_entity errorCode=SELF_TRANSFER_FORBIDDEN source=accounts-service");
        return new ApiErrorResponse("SELF_TRANSFER_FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleInsufficientFunds(InsufficientFundsException exception) {
        log.warn("Account request rejected status=unprocessable_entity errorCode=INSUFFICIENT_FUNDS source=accounts-service");
        return new ApiErrorResponse("INSUFFICIENT_FUNDS", exception.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleCurrencyMismatch(CurrencyMismatchException exception) {
        log.warn("Account request rejected status=unprocessable_entity errorCode=CURRENCY_MISMATCH source=accounts-service");
        return new ApiErrorResponse("CURRENCY_MISMATCH", exception.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleConcurrentUpdate(RuntimeException exception) {
        log.warn("Account request rejected status=conflict errorCode=CONCURRENT_UPDATE source=accounts-service");
        return new ApiErrorResponse("CONCURRENT_UPDATE", "Данные были изменены другим запросом");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIdempotencyConflict(IdempotencyConflictException exception) {
        log.warn("Account request rejected status=conflict errorCode=IDEMPOTENCY_CONFLICT source=accounts-service");
        return new ApiErrorResponse("IDEMPOTENCY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(OperationInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleOperationInProgress(OperationInProgressException exception) {
        log.warn("Account request rejected status=conflict errorCode=OPERATION_IN_PROGRESS source=accounts-service");
        return new ApiErrorResponse("OPERATION_IN_PROGRESS", exception.getMessage());
    }

    @ExceptionHandler(OperationAlreadyFailedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleOperationAlreadyFailed(OperationAlreadyFailedException exception) {
        log.warn("Account request rejected status=conflict errorCode=OPERATION_ALREADY_FAILED source=accounts-service");
        return new ApiErrorResponse("OPERATION_ALREADY_FAILED", exception.getMessage());
    }

    @ExceptionHandler({InvalidBirthdateException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(Exception exception) {
        log.warn("Account request rejected status=bad_request errorCode=VALIDATION_ERROR source=accounts-service");
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MissingPreferredUsernameException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleMissingPreferredUsername(MissingPreferredUsernameException exception) {
        log.warn("Account request rejected status=unauthorized errorCode=UNAUTHORIZED source=accounts-service");
        return new ApiErrorResponse("UNAUTHORIZED", exception.getMessage());
    }
}
