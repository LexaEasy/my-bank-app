package ru.practicum.bank.transfer.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.transfer.service.TransferService;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void shouldTransferMoney() throws Exception {
        when(transferService.transfer("ivan", request("olga", "200.00"))).thenReturn(new TransferResponse(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "RUB",
                "Transfer completed"
        ));

        mockMvc.perform(post("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "200.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderLogin").value("ivan"))
                .andExpect(jsonPath("$.recipientLogin").value("olga"))
                .andExpect(jsonPath("$.senderBalance").value("800.00"))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.message").value("Transfer completed"));

        verify(transferService).transfer("ivan", request("olga", "200.00"));
    }

    @Test
    void shouldAcceptCnyTransferRequest() throws Exception {
        when(transferService.transfer("ivan", request("olga", "200.00", Currency.CNY))).thenReturn(new TransferResponse(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "CNY",
                "Transfer completed"
        ));

        mockMvc.perform(post("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "200.00",
                                  "currency": "CNY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CNY"));

        verify(transferService).transfer("ivan", request("olga", "200.00", Currency.CNY));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "200.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnUnprocessableEntityForSelfTransfer() throws Exception {
        when(transferService.transfer("ivan", request("ivan", "200.00")))
                .thenThrow(new SelfTransferForbiddenException());

        mockMvc.perform(post("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "ivan",
                                  "amount": "200.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER_FORBIDDEN"));
    }

    private JwtAuthenticationToken jwtAuthentication(String login) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("preferred_username", login)
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private TransferRequest request(String recipientLogin, String amount) {
        return request(recipientLogin, amount, Currency.RUB);
    }

    private TransferRequest request(String recipientLogin, String amount, Currency currency) {
        return new TransferRequest(recipientLogin, new BigDecimal(amount), currency);
    }
}
