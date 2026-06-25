package ru.practicum.bank.exchange.contract;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.bank.exchange.service.ExchangeService;
import ru.practicum.bank.exchange.web.ExchangeController;
import ru.practicum.bank.exchange.web.ExchangeExceptionHandler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public abstract class ExchangeContractBase {

    @BeforeEach
    void setUp() {
        var exchangeService = new ExchangeService(Clock.fixed(
                Instant.parse("2026-06-25T10:00:00Z"),
                ZoneOffset.UTC
        ));
        var mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeController(exchangeService))
                .setControllerAdvice(new ExchangeExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .build();

        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    private JsonMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
