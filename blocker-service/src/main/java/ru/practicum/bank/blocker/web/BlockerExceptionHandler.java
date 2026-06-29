package ru.practicum.bank.blocker.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.bank.blocker.dto.ApiErrorResponse;
import ru.practicum.bank.blocker.exception.InvalidOperationRequestException;

@RestControllerAdvice
public class BlockerExceptionHandler {

    @ExceptionHandler(InvalidOperationRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidOperationRequest(InvalidOperationRequestException exception) {
        return new ApiErrorResponse("INVALID_OPERATION_REQUEST", exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(Exception exception) {
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }
}
