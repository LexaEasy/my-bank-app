package ru.practicum.bank.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.bank.accounts.model.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByLogin(String login);

    List<Account> findAllByLoginNot(String login);
}
