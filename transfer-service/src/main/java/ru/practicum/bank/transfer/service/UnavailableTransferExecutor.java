package ru.practicum.bank.transfer.service;

import org.springframework.stereotype.Component;
import ru.practicum.bank.transfer.exception.TransferExecutionException;

@Component
public class UnavailableTransferExecutor implements TransferExecutor {

    @Override
    public TransferResult execute(TransferOperation operation) {
        throw new TransferExecutionException("Transfer executor is not configured");
    }
}
