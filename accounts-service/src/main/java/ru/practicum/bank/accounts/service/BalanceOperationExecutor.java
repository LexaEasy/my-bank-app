package ru.practicum.bank.accounts.service;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

    private final AccountRepository accountRepository;

    public BalanceOperationExecutor(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BalanceResponse deposit(BalanceOperationRequest request) {
        validateAmount(request.amount());
        Account account = findAccount(request.login());

        account.setBalance(account.getBalance().add(request.amount()));

        return toBalanceResponse(accountRepository.save(account));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BalanceResponse withdraw(BalanceOperationRequest request) {
        validateAmount(request.amount());
        Account account = findAccount(request.login());

        withdraw(account, request.amount());

        return toBalanceResponse(accountRepository.save(account));
    }

    @Retryable(
            retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferBalanceResponse transfer(TransferBalanceRequest request) {
        if (request.senderLogin().equals(request.recipientLogin())) {
            throw new SelfTransferForbiddenException();
        }
        validateAmount(request.amount());

        Account sender = findAccount(request.senderLogin());
        Account recipient = accountRepository.findByLogin(request.recipientLogin())
                .orElseThrow(() -> new RecipientNotFoundException(request.recipientLogin()));

        withdraw(sender, request.amount());
        recipient.setBalance(recipient.getBalance().add(request.amount()));

        saveInDeterministicOrder(sender, recipient);

        return new TransferBalanceResponse(
                sender.getLogin(),
                recipient.getLogin(),
                sender.getBalance(),
                request.currency().name()
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountScaleException();
        }
    }

    private void withdraw(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
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
