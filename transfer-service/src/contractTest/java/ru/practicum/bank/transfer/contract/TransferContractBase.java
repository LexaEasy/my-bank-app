package ru.practicum.bank.transfer.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.service.TransferService;
import ru.practicum.bank.transfer.web.TransferController;
import ru.practicum.bank.transfer.web.TransferExceptionHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public abstract class TransferContractBase {

    @BeforeEach
    void setUp() {
        var transferService = mock(TransferService.class);
        when(transferService.transfer(eq("ivan"), any(TransferRequest.class), any(UUID.class)))
                .thenReturn(new TransferResponse(
                        "ivan",
                        "petr",
                        new BigDecimal("850.00"),
                        "RUB",
                        "Transfer completed"
                ));

        var mockMvc = MockMvcBuilders.standaloneSetup(new TransferController(transferService))
                .setControllerAdvice(new TransferExceptionHandler())
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
