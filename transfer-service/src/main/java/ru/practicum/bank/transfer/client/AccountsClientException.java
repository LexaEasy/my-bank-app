package ru.practicum.bank.transfer.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class AccountsClientException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public AccountsClientException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, null);
    }

    public AccountsClientException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public AccountsClientException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
