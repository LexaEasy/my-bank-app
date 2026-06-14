package ru.practicum.bank.frontui.client;

public class GatewayClientException extends RuntimeException {

    public GatewayClientException(String message) {
        super(message);
    }

    public GatewayClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
