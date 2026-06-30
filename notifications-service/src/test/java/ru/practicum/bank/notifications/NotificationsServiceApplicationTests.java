package ru.practicum.bank.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationsServiceApplicationTests {

    @Autowired
    private Environment environment;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
        assertThat(environment.getProperty("spring.kafka.bootstrap-servers"))
                .isEqualTo("localhost:9092");
        assertThat(environment.getProperty("spring.kafka.consumer.group-id"))
                .isEqualTo("bank-notifications");
        assertThat(environment.getProperty("bank.kafka.notifications-topic"))
                .isEqualTo("bank.notifications");
        assertThat(environment.getProperty("bank.kafka.notifications-dlt-topic"))
                .isEqualTo("bank.notifications.dlt");
    }
}
