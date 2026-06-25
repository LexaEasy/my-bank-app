package ru.practicum.bank.exchangegenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.bank.exchangegenerator.client.ExchangeClient;

@SpringBootTest
class ExchangeGeneratorApplicationTests {

    @MockitoBean
    private ExchangeClient exchangeClient;

    @Test
    void contextLoads() {
    }
}
