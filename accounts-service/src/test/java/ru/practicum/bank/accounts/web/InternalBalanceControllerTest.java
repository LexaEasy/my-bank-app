package ru.practicum.bank.accounts.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;
import ru.practicum.bank.accounts.exception.IdempotencyConflictException;
import ru.practicum.bank.accounts.exception.InvalidAmountScaleException;
import ru.practicum.bank.accounts.exception.OperationInProgressException;
import ru.practicum.bank.accounts.service.BalanceService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalBalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalBalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    void shouldDepositMoney() throws Exception {
        when(balanceService.deposit(any())).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "RUB"
        ));

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("ivan"))
                .andExpect(jsonPath("$.balance").value("1250.00"))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    void shouldWithdrawMoney() throws Exception {
        when(balanceService.withdraw(any())).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("900.00"),
                "RUB"
        ));

        mockMvc.perform(post("/api/accounts/internal/balance/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("900.00"));
    }

    @Test
    void shouldTransferMoney() throws Exception {
        when(balanceService.transfer(any())).thenReturn(new TransferBalanceResponse(
                "ivan",
                "petr",
                new BigDecimal("850.00"),
                "RUB"
        ));

        mockMvc.perform(post("/api/accounts/internal/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderLogin": "ivan",
                                  "recipientLogin": "petr",
                                  "amount": "150.00",
                                  "currency": "RUB",
                                  "operationId": "operation-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderLogin").value("ivan"))
                .andExpect(jsonPath("$.recipientLogin").value("petr"))
                .andExpect(jsonPath("$.senderBalance").value("850.00"))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    void shouldReturnInvalidAmountScaleError() throws Exception {
        when(balanceService.deposit(any())).thenThrow(new InvalidAmountScaleException());

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT_SCALE"));
    }

    @Test
    void shouldReturnIdempotencyConflictError() throws Exception {
        when(balanceService.deposit(any())).thenThrow(new IdempotencyConflictException("operation-1"));

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void shouldReturnOperationInProgressError() throws Exception {
        when(balanceService.deposit(any())).thenThrow(new OperationInProgressException("operation-1"));

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_IN_PROGRESS"));
    }

    private String operationRequest() {
        return """
                {
                  "login": "ivan",
                  "amount": "250.00",
                  "currency": "RUB",
                  "operationId": "operation-1"
                }
                """;
    }
}
