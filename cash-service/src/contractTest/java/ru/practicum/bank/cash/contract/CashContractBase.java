package ru.practicum.bank.cash.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.service.CashService;
import ru.practicum.bank.cash.web.CashController;
import ru.practicum.bank.cash.web.CashExceptionHandler;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public abstract class CashContractBase {

    @BeforeEach
    void setUp() {
        var cashService = mock(CashService.class);
        when(cashService.deposit(eq("ivan"), any(CashOperationRequest.class)))
                .thenReturn(new CashOperationResponse(
                        new BigDecimal("1250.00"),
                        "RUB",
                        "Счёт пополнен"
                ));
        when(cashService.withdraw(eq("ivan"), any(CashOperationRequest.class)))
                .thenReturn(new CashOperationResponse(
                        new BigDecimal("900.00"),
                        "RUB",
                        "Деньги сняты со счёта"
                ));

        var mockMvc = MockMvcBuilders.standaloneSetup(new CashController(cashService))
                .setControllerAdvice(new CashExceptionHandler())
                .defaultRequest(get("/").principal(jwtAuthentication("ivan")))
                .build();

        RestAssuredMockMvc.mockMvc(mockMvc);
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
