package ru.practicum.bank.accounts.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;
import ru.practicum.bank.accounts.exception.CurrencyMismatchException;
import ru.practicum.bank.accounts.exception.IdempotencyConflictException;
import ru.practicum.bank.accounts.exception.InsufficientFundsException;
import ru.practicum.bank.accounts.exception.InvalidAmountScaleException;
import ru.practicum.bank.accounts.exception.OperationInProgressException;
import ru.practicum.bank.accounts.exception.SelfTransferForbiddenException;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.accounts.service.BalanceService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void shouldAcceptUsdBalanceOperationRequest() throws Exception {
        when(balanceService.deposit(any())).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "USD"
        ));

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest("USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(balanceService).deposit(new BalanceOperationRequest(
                "ivan",
                new BigDecimal("250.00"),
                Currency.USD,
                "operation-1"
        ));
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
    void shouldAcceptConvertedTransferAmounts() throws Exception {
        when(balanceService.transfer(any())).thenReturn(new TransferBalanceResponse(
                "ivan",
                "petr",
                new BigDecimal("900.00"),
                "USD"
        ));

        mockMvc.perform(post("/api/accounts/internal/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderLogin": "ivan",
                                  "recipientLogin": "petr",
                                  "amount": "100.00",
                                  "currency": "USD",
                                  "recipientAmount": "741.94",
                                  "recipientCurrency": "CNY",
                                  "operationId": "operation-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderBalance").value("900.00"))
                .andExpect(jsonPath("$.currency").value("USD"));
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

    @Test
    void shouldReturnUnprocessableEntityWhenFundsAreInsufficient() throws Exception {
        when(balanceService.withdraw(any())).thenThrow(new InsufficientFundsException());

        mockMvc.perform(post("/api/accounts/internal/balance/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void shouldReturnUnprocessableEntityWhenCurrencyDoesNotMatch() throws Exception {
        when(balanceService.deposit(any())).thenThrow(new CurrencyMismatchException());

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest("USD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void shouldReturnUnprocessableEntityForSelfTransfer() throws Exception {
        when(balanceService.transfer(any())).thenThrow(new SelfTransferForbiddenException());

        mockMvc.perform(post("/api/accounts/internal/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderLogin": "ivan",
                                  "recipientLogin": "ivan",
                                  "amount": "150.00",
                                  "currency": "RUB",
                                  "operationId": "operation-1"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER_FORBIDDEN"));
    }

    @Test
    void shouldReturnConflictForConcurrentUpdate() throws Exception {
        when(balanceService.withdraw(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, 1L));

        mockMvc.perform(post("/api/accounts/internal/balance/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    private String operationRequest() {
        return operationRequest("RUB");
    }

    private String operationRequest(String currency) {
        return """
                {
                  "login": "ivan",
                  "amount": "250.00",
                  "currency": "%s",
                  "operationId": "operation-1"
                }
                """.formatted(currency);
    }
}
