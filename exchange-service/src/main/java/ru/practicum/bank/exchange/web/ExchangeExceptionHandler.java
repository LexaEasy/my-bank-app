package ru.practicum.bank.exchange.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.bank.exchange.dto.ApiErrorResponse;
import ru.practicum.bank.exchange.exception.InvalidAmountException;
import ru.practicum.bank.exchange.exception.InvalidRateException;

@RestControllerAdvice
public class ExchangeExceptionHandler {

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAmount(InvalidAmountException exception) {
        return new ApiErrorResponse("INVALID_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidRateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidRate(InvalidRateException exception) {
        return new ApiErrorResponse("INVALID_RATE", exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(Exception exception) {
        return new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage());
    }
}
