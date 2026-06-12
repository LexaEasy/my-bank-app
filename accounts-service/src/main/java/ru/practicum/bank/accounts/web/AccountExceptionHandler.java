package ru.practicum.bank.accounts.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.accounts.dto.ApiErrorResponse;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.exception.InvalidBirthdateException;
import ru.practicum.bank.accounts.exception.MissingPreferredUsernameException;

@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleAccountNotFound(AccountNotFoundException exception) {
        return new ApiErrorResponse("ACCOUNT_NOT_FOUND", exception.getMessage());
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
