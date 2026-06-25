package ru.practicum.bank.accounts.web;

import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.dto.RecipientResponse;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.exception.MissingPreferredUsernameException;
import ru.practicum.bank.accounts.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountResponse getCurrentAccount(JwtAuthenticationToken authentication) {
        return accountService.getCurrentAccount(getLogin(authentication));
    }

    @PutMapping("/me")
    public AccountResponse updateCurrentAccount(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        return accountService.updateCurrentAccount(getLogin(authentication), request);
    }

    @GetMapping("/recipients")
    public List<RecipientResponse> getRecipients(JwtAuthenticationToken authentication) {
        return accountService.getRecipients(getLogin(authentication));
    }

    private String getLogin(JwtAuthenticationToken authentication) {
        if (authentication == null) {
            throw new MissingPreferredUsernameException();
        }

        String login = authentication.getToken().getClaimAsString("preferred_username");
        if (login == null || login.isBlank()) {
            throw new MissingPreferredUsernameException();
        }

        return login;
    }
}
