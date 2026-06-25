package ru.practicum.bank.accounts.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.dto.RecipientResponse;
import ru.practicum.bank.accounts.model.Account;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getLogin(),
                account.getName(),
                account.getBirthdate(),
                account.getBalance(),
                account.getCurrency().name()
        );
    }

    public RecipientResponse toRecipientResponse(Account account) {
        return new RecipientResponse(
                account.getLogin(),
                account.getName()
        );
    }
}
