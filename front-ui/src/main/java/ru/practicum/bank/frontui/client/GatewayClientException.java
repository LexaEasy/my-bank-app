package ru.practicum.bank.frontui.client;

public class GatewayClientException extends RuntimeException {

    private final boolean technical;

    public GatewayClientException(String message) {
        super(message);
        this.technical = false;
    }

    public GatewayClientException(String message, Throwable cause) {
        super(message, cause);
        this.technical = true;
    }

    public boolean isTechnical() {
        return technical;
    }
}
