package ru.practicum.bank.accounts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.dto.RecipientResponse;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.exception.InvalidBirthdateException;
import ru.practicum.bank.accounts.mapper.AccountMapper;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, AccountMapper accountMapper, Clock clock) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountResponse getCurrentAccount(String login) {
        return accountMapper.toResponse(findAccount(login));
    }

    @Transactional
    public AccountResponse updateCurrentAccount(String login, UpdateAccountRequest request) {
        if (!isAdult(request.birthdate())) {
            throw new InvalidBirthdateException();
        }

        Account account = findAccount(login);
        account.updateProfile(request.name(), request.birthdate());

        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<RecipientResponse> getRecipients(String currentLogin) {
        return accountRepository.findAllByLoginNot(currentLogin).stream()
                .map(accountMapper::toRecipientResponse)
                .toList();
    }

    private Account findAccount(String login) {
        return accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException(login));
    }

    private boolean isAdult(LocalDate birthdate) {
        return !birthdate.plusYears(18).isAfter(LocalDate.now(clock));
    }
}
