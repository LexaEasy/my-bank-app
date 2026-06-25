package ru.practicum.bank.blocker.exception;

public class InvalidOperationRequestException extends RuntimeException {

    public InvalidOperationRequestException(String message) {
        super(message);
    }
}
