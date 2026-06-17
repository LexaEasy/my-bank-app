package ru.practicum.bank.accounts.service;

import org.springframework.stereotype.Service;
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
import ru.practicum.bank.accounts.model.Currency;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final AccountRepository accountRepository;
    private final IdempotencyService idempotencyService;

    public BalanceService(AccountRepository accountRepository, IdempotencyService idempotencyService) {
        this.accountRepository = accountRepository;
        this.idempotencyService = idempotencyService;
    }

    public BalanceResponse deposit(BalanceOperationRequest request) {
        return idempotencyService.execute(
                request.operationId(),
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> doDeposit(request)
        );
    }

    public BalanceResponse withdraw(BalanceOperationRequest request) {
        return idempotencyService.execute(
                request.operationId(),
                "WITHDRAW",
                request,
                BalanceResponse.class,
                () -> doWithdraw(request)
        );
    }

    public TransferBalanceResponse transfer(TransferBalanceRequest request) {
        return idempotencyService.execute(
                request.operationId(),
                "TRANSFER",
                request,
                TransferBalanceResponse.class,
                () -> doTransfer(request)
        );
    }

    private BalanceResponse doDeposit(BalanceOperationRequest request) {
        validateAmount(request.amount());
        Account account = findAccount(request.login());

        account.setBalance(account.getBalance().add(request.amount()));

        return toBalanceResponse(accountRepository.save(account));
    }

    private BalanceResponse doWithdraw(BalanceOperationRequest request) {
        validateAmount(request.amount());
        Account account = findAccount(request.login());

        withdraw(account, request.amount());

        return toBalanceResponse(accountRepository.save(account));
    }

    private TransferBalanceResponse doTransfer(TransferBalanceRequest request) {
        if (request.senderLogin().equals(request.recipientLogin())) {
            throw new SelfTransferForbiddenException();
        }
        validateAmount(request.amount());

        Account sender = findAccount(request.senderLogin());
        Account recipient = accountRepository.findByLogin(request.recipientLogin())
                .orElseThrow(() -> new RecipientNotFoundException(request.recipientLogin()));

        withdraw(sender, request.amount());
        recipient.setBalance(recipient.getBalance().add(request.amount()));
        accountRepository.save(sender);
        accountRepository.save(recipient);

        return new TransferBalanceResponse(
                sender.getLogin(),
                recipient.getLogin(),
                sender.getBalance(),
                Currency.RUB.name()
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

    private BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(
                account.getLogin(),
                account.getBalance(),
                account.getCurrency().name()
        );
    }
}
