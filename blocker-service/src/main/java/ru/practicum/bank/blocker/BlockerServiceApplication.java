package ru.practicum.bank.blocker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class BlockerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlockerServiceApplication.class, args);
    }
}
