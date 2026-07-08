package ru.practicum.bank.accounts.exception;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException() {
        super("Валюта операции не совпадает с валютой счёта");
    }
}
