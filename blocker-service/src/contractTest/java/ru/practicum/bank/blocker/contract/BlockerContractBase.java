package ru.practicum.bank.blocker.contract;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.bank.blocker.config.BlockerProperties;
import ru.practicum.bank.blocker.service.BlockerService;
import ru.practicum.bank.blocker.web.BlockerController;
import ru.practicum.bank.blocker.web.BlockerExceptionHandler;

import java.math.BigDecimal;

public abstract class BlockerContractBase {

    @BeforeEach
    void setUp() {
        var blockerService = new BlockerService(new BlockerProperties(new BigDecimal("100000.00")));
        var mockMvc = MockMvcBuilders.standaloneSetup(new BlockerController(blockerService))
                .setControllerAdvice(new BlockerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder().build()))
                .build();

        RestAssuredMockMvc.mockMvc(mockMvc);
    }
}
