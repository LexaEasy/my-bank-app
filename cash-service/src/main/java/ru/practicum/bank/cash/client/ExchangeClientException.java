package ru.practicum.bank.cash.client;

public class ExchangeClientException extends RuntimeException {

    public ExchangeClientException(String message) {
        super(message);
    }

    public ExchangeClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
