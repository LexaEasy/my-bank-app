package ru.practicum.bank.frontui.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationContractTest {

    @Test
    void shouldContainRequiredJsonFieldsAndMasking() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            assertThat(stream).isNotNull();
            var configuration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(configuration)
                    .contains("@timestamp")
                    .contains("\"level\"")
                    .contains("\"application\"")
                    .contains("\"logger_name\"")
                    .contains("\"thread_name\"")
                    .contains("\"message\"")
                    .contains("\"traceId\"")
                    .contains("\"spanId\"")
                    .contains("\"stack_trace\"")
                    .contains("MaskingJsonGeneratorDecorator")
                    .contains("password|token|authorization|client_secret|secret")
                    .contains("<springProfile name=\"test\">");
        }
    }
}
