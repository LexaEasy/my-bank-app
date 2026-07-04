package ru.practicum.bank.blocker.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.blocker.exception.InvalidOperationRequestException;
import ru.practicum.bank.blocker.service.BlockerService;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BlockerController.class)
@AutoConfigureMockMvc(addFilters = false)
class BlockerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockerService blockerService;

    @Test
    void shouldAllowOperation() throws Exception {
        when(blockerService.check(any())).thenReturn(new OperationCheckResponse(true, null));

        mockMvc.perform(post("/api/blocker/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-1",
                                  "operationType": "DEPOSIT",
                                  "login": "ivan",
                                  "amount": "1000.00",
                                  "currency": "RUB",
                                  "normalizedAmount": "1000.00",
                                  "baseCurrency": "RUB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void shouldReturnBlockedOperation() throws Exception {
        when(blockerService.check(any()))
                .thenReturn(new OperationCheckResponse(false, "Operation amount exceeds blocker limit"));

        mockMvc.perform(post("/api/blocker/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-1",
                                  "operationType": "TRANSFER",
                                  "sender": "ivan",
                                  "recipient": "olga",
                                  "amount": "100000.01",
                                  "currency": "USD",
                                  "normalizedAmount": "9000000.90",
                                  "baseCurrency": "RUB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("Operation amount exceeds blocker limit"));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/api/blocker/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationType": "DEPOSIT",
                                  "login": "ivan",
                                  "amount": "1000.00",
                                  "currency": "RUB",
                                  "normalizedAmount": "1000.00",
                                  "baseCurrency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnInvalidOperationRequestError() throws Exception {
        when(blockerService.check(any()))
                .thenThrow(new InvalidOperationRequestException("login is required for cash operation"));

        mockMvc.perform(post("/api/blocker/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-1",
                                  "operationType": "WITHDRAW",
                                  "amount": "1000.00",
                                  "currency": "RUB",
                                  "normalizedAmount": "1000.00",
                                  "baseCurrency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION_REQUEST"));
    }
}
