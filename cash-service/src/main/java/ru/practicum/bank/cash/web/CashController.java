package ru.practicum.bank.cash.web;

import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.exception.MissingPreferredUsernameException;
import ru.practicum.bank.cash.service.CashService;

import java.util.UUID;

@RestController
public class CashController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @PostMapping("/api/cash/deposit")
    public CashOperationResponse deposit(
            JwtAuthenticationToken authentication,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
            @Valid @RequestBody CashOperationRequest request
    ) {
        return cashService.deposit(getLogin(authentication), request, idempotencyKey);
    }

    @PostMapping("/api/cash/withdraw")
    public CashOperationResponse withdraw(
            JwtAuthenticationToken authentication,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
            @Valid @RequestBody CashOperationRequest request
    ) {
        return cashService.withdraw(getLogin(authentication), request, idempotencyKey);
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
