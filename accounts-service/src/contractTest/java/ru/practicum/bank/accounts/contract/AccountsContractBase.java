package ru.practicum.bank.accounts.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.RecipientResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.service.AccountService;
import ru.practicum.bank.accounts.service.BalanceService;
import ru.practicum.bank.accounts.web.AccountController;
import ru.practicum.bank.accounts.web.AccountExceptionHandler;
import ru.practicum.bank.accounts.web.InternalBalanceController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public abstract class AccountsContractBase {

    @BeforeEach
    void setUp() {
        var accountService = mock(AccountService.class);
        var balanceService = mock(BalanceService.class);

        when(accountService.getCurrentAccount("ivan")).thenReturn(account());
        when(accountService.updateCurrentAccount(eq("ivan"), any(UpdateAccountRequest.class)))
                .thenReturn(updatedAccount());
        when(accountService.getRecipients("ivan")).thenReturn(List.of(
                new RecipientResponse("petr", "Петров Пётр"),
                new RecipientResponse("anna", "Сидорова Анна")
        ));
        when(balanceService.deposit(any())).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "RUB"
        ));
        when(balanceService.withdraw(any())).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("900.00"),
                "RUB"
        ));
        when(balanceService.transfer(any())).thenReturn(new TransferBalanceResponse(
                "ivan",
                "petr",
                new BigDecimal("850.00"),
                "RUB"
        ));

        var mockMvc = MockMvcBuilders.standaloneSetup(
                        new AccountController(accountService),
                        new InternalBalanceController(balanceService)
                )
                .setControllerAdvice(new AccountExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .defaultRequest(get("/").principal(jwtAuthentication("ivan")))
                .build();

        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    private JsonMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    private AccountResponse account() {
        return new AccountResponse(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                "RUB"
        );
    }

    private AccountResponse updatedAccount() {
        return new AccountResponse(
                "ivan",
                "Иван Иванов",
                LocalDate.of(1992, 5, 10),
                new BigDecimal("1000.00"),
                "RUB"
        );
    }

    private JwtAuthenticationToken jwtAuthentication(String login) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("preferred_username", login)
                .build();

        return new JwtAuthenticationToken(jwt);
    }
}
