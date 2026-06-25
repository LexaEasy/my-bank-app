package ru.practicum.bank.notifications.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.bank.notifications.service.NotificationService;
import ru.practicum.bank.notifications.web.NotificationController;
import ru.practicum.bank.notifications.web.NotificationExceptionHandler;

import static org.mockito.Mockito.mock;

public abstract class NotificationsContractBase {

    @BeforeEach
    void setUp() {
        var notificationService = mock(NotificationService.class);
        var mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new NotificationExceptionHandler())
                .build();

        RestAssuredMockMvc.mockMvc(mockMvc);
    }
}
