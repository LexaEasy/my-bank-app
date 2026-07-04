package ru.practicum.bank.transfer.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class AccountsClientException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String code;

    public AccountsClientException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, "ACCOUNTS_SERVICE_UNAVAILABLE", null);
    }

    public AccountsClientException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, "ACCOUNTS_SERVICE_UNAVAILABLE", cause);
    }

    public AccountsClientException(String message, HttpStatusCode statusCode, Throwable cause) {
        this(message, statusCode, defaultCode(statusCode), cause);
    }

    public AccountsClientException(String message, HttpStatusCode statusCode, String code, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    private static String defaultCode(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.CONFLICT.value()) {
            return "IDEMPOTENCY_CONFLICT";
        }
        return "ACCOUNTS_SERVICE_UNAVAILABLE";
    }
}
