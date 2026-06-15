package ru.practicum.bank.accounts.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Недостаточно средств");
    }
}
