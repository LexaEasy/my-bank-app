package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.exception.InvalidBirthdateException;
import ru.practicum.bank.accounts.mapper.AccountMapper;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, new AccountMapper(), clock);
    }

    @Test
    void shouldReturnCurrentAccount() {
        var account = account("ivan", "Иванов Иван", LocalDate.of(1990, 1, 15));
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));

        var response = accountService.getCurrentAccount("ivan");

        assertThat(response.login()).isEqualTo("ivan");
        assertThat(response.name()).isEqualTo("Иванов Иван");
        assertThat(response.birthdate()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.currency()).isEqualTo("RUB");
    }

    @Test
    void shouldUpdateCurrentAccountProfile() {
        var account = account("ivan", "Иванов Иван", LocalDate.of(1990, 1, 15));
        var request = new UpdateAccountRequest("Иван Иванов", LocalDate.of(1992, 5, 10));
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response = accountService.updateCurrentAccount("ivan", request);

        assertThat(response.name()).isEqualTo("Иван Иванов");
        assertThat(response.birthdate()).isEqualTo(LocalDate.of(1992, 5, 10));
        verify(accountRepository).save(account);
    }

    @Test
    void shouldReturnRecipientsWithoutCurrentUser() {
        when(accountRepository.findAllByLoginNot("ivan")).thenReturn(List.of(
                account("petr", "Петров Пётр", LocalDate.of(1988, 3, 20)),
                account("anna", "Сидорова Анна", LocalDate.of(1995, 7, 10))
        ));

        var recipients = accountService.getRecipients("ivan");

        assertThat(recipients)
                .extracting("login")
                .containsExactly("petr", "anna");
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getCurrentAccount("unknown"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: unknown");
    }

    @Test
    void shouldRejectUnderageBirthdate() {
        var request = new UpdateAccountRequest("Иван Иванов", LocalDate.of(2010, 6, 12));

        assertThatThrownBy(() -> accountService.updateCurrentAccount("ivan", request))
                .isInstanceOf(InvalidBirthdateException.class)
                .hasMessage("Account owner must be at least 18 years old");
        verify(accountRepository, never()).findByLogin(any());
        verify(accountRepository, never()).save(any());
    }

    private Account account(String login, String name, LocalDate birthdate) {
        return new Account(login, name, birthdate, new BigDecimal("1000.00"), Currency.RUB);
    }
}
