package ru.practicum.bank.cash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.practicum.bank.common.notification.NotificationProducerConfiguration;

@Import(NotificationProducerConfiguration.class)
@SpringBootApplication
public class CashServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashServiceApplication.class, args);
    }
}
