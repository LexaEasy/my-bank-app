package ru.practicum.bank.notifications.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.notifications.dto.NotificationRequest;
import ru.practicum.bank.notifications.service.NotificationService;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void shouldAcceptNotification() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "ivan",
                                  "type": "CASH_DEPOSIT",
                                  "message": "Счёт пополнен на 250.00 RUB",
                                  "operationId": "operation-1"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(notificationService).notify(new NotificationRequest(
                "ivan",
                "CASH_DEPOSIT",
                "Счёт пополнен на 250.00 RUB",
                "operation-1"
        ));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "",
                                  "type": "CASH_DEPOSIT",
                                  "message": "Счёт пополнен на 250.00 RUB",
                                  "operationId": "operation-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
