package ru.practicum.bank.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationsServiceApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
        assertThat(environment.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(environment.getProperty("spring.kafka.bootstrap-servers"))
                .isEqualTo("localhost:9092");
        assertThat(environment.getProperty("spring.kafka.consumer.group-id"))
                .isEqualTo("bank-notifications");
        assertThat(environment.getProperty("spring.kafka.consumer.enable-auto-commit"))
                .isEqualTo("false");
        assertThat(environment.getProperty("spring.kafka.consumer.auto-offset-reset"))
                .isEqualTo("earliest");
        assertThat(environment.getProperty("spring.kafka.listener.ack-mode"))
                .isEqualTo("record");
        assertThat(environment.getProperty("bank.kafka.notifications-topic"))
                .isEqualTo("bank.notifications");
        assertThat(environment.getProperty("bank.kafka.notifications-dlt-topic"))
                .isEqualTo("bank.notifications.dlt");
        assertThat(environment.getProperty("bank.kafka.notifications-partitions"))
                .isEqualTo("3");
        assertThat(environment.getProperty("bank.kafka.notifications-dlt-partitions"))
                .isEqualTo("3");
        assertThat(environment.getProperty("bank.kafka.notifications-replication-factor"))
                .isEqualTo("1");
        assertThat(environment.getProperty("bank.kafka.notifications-dlt-retention-ms"))
                .isEqualTo("604800000");
    }
}
