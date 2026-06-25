package ru.practicum.bank.transfer.service;

public interface TransferExecutor {

    TransferResult execute(TransferOperation operation);
}
