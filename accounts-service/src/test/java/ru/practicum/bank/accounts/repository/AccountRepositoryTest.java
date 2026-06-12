package ru.practicum.bank.accounts.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.bank.accounts.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldFindInitialAccountByLogin() {
        var account = accountRepository.findByLogin("ivan");

        assertThat(account).isPresent();
        assertThat(account.get().getName()).isEqualTo("Иванов Иван");
        assertThat(account.get().getBirthdate()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(account.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(account.get().getCurrency()).isEqualTo(Currency.RUB);
    }

    @Test
    void shouldLoadAllInitialAccounts() {
        assertThat(accountRepository.count()).isEqualTo(3);
    }
}
