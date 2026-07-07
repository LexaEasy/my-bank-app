package ru.practicum.bank.accounts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.exception.InsufficientFundsException;
import ru.practicum.bank.accounts.exception.InvalidAmountException;
import ru.practicum.bank.accounts.exception.InvalidAmountScaleException;
import ru.practicum.bank.accounts.exception.RecipientNotFoundException;
import ru.practicum.bank.accounts.exception.SelfTransferForbiddenException;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class BalanceOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(BalanceOperationExecutor.class);

    private final AccountRepository accountRepository;

    public BalanceOperationExecutor(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BalanceResponse deposit(BalanceOperationRequest request) {
        validateAmount(request.amount(), request.operationId(), "DEPOSIT", request.currency().name());
        Account account = findAccount(request.login());

        account.setBalance(account.getBalance().add(request.amount()));

        return toBalanceResponse(accountRepository.save(account));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BalanceResponse withdraw(BalanceOperationRequest request) {
        validateAmount(request.amount(), request.operationId(), "WITHDRAW", request.currency().name());
        Account account = findAccount(request.login());

        withdraw(account, request.amount(), request.operationId(), "WITHDRAW", request.currency().name());

        return toBalanceResponse(accountRepository.save(account));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TransferBalanceResponse transfer(TransferBalanceRequest request) {
        if (request.senderLogin().equals(request.recipientLogin())) {
            log.warn(
                    "Balance operation rejected operationId={} operationType=TRANSFER currency={} status=rejected errorCode=SELF_TRANSFER_FORBIDDEN source=accounts-service",
                    request.operationId(),
                    request.currency()
            );
            throw new SelfTransferForbiddenException();
        }
        validateAmount(request.amount(), request.operationId(), "TRANSFER", request.currency().name());
        validateAmount(
                request.resolvedRecipientAmount(),
                request.operationId(),
                "TRANSFER",
                request.resolvedRecipientCurrency().name()
        );

        Account sender = findAccount(request.senderLogin());
        Account recipient = accountRepository.findByLogin(request.recipientLogin())
                .orElseThrow(() -> new RecipientNotFoundException(request.recipientLogin()));

        withdraw(sender, request.amount(), request.operationId(), "TRANSFER", request.currency().name());
        recipient.setBalance(recipient.getBalance().add(request.resolvedRecipientAmount()));

        saveInDeterministicOrder(sender, recipient);

        return new TransferBalanceResponse(
                sender.getLogin(),
                recipient.getLogin(),
                sender.getBalance(),
                request.currency().name()
        );
    }

    private void validateAmount(BigDecimal amount, String operationId, String operationType, String currency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(
                    "Balance operation rejected operationId={} operationType={} currency={} status=rejected errorCode=INVALID_AMOUNT source=accounts-service",
                    operationId,
                    operationType,
                    currency
            );
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            log.warn(
                    "Balance operation rejected operationId={} operationType={} currency={} status=rejected errorCode=INVALID_AMOUNT_SCALE source=accounts-service",
                    operationId,
                    operationType,
                    currency
            );
            throw new InvalidAmountScaleException();
        }
    }

    private void withdraw(Account account, BigDecimal amount, String operationId, String operationType, String currency) {
        if (account.getBalance().compareTo(amount) < 0) {
            log.warn(
                    "Balance operation rejected operationId={} operationType={} currency={} status=rejected errorCode=INSUFFICIENT_FUNDS source=accounts-service",
                    operationId,
                    operationType,
                    currency
            );
            throw new InsufficientFundsException();
        }
        account.setBalance(account.getBalance().subtract(amount));
    }

    private Account findAccount(String login) {
        return accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException(login));
    }

    private void saveInDeterministicOrder(Account first, Account second) {
        List.of(first, second).stream()
                .sorted(Comparator.comparing(Account::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(accountRepository::save);
    }

    private BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(
                account.getLogin(),
                account.getBalance(),
                account.getCurrency().name()
        );
    }
}
