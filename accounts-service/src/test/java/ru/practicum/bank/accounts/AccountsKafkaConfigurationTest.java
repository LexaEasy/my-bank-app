package ru.practicum.bank.accounts;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.practicum.bank.common.notification.KafkaNotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationProducerConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccountsKafkaConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(
                    KafkaPropertiesConfiguration.class,
                    NotificationProducerConfiguration.class,
                    TestBeansConfiguration.class
            );

    @Test
    void shouldConfigureKafkaProducerAndNotificationPublisher() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.kafka.bootstrap-servers"))
                    .isEqualTo("localhost:9092");
            assertThat(context.getEnvironment().getProperty("bank.kafka.notifications-topic"))
                    .isEqualTo("bank.notifications");

            var properties = context.getBean(KafkaProperties.class).buildProducerProperties(null);
            assertThat(properties)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
                    .containsEntry(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE)
                    .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000")
                    .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000")
                    .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")
                    .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                    .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

            assertThat(context).hasSingleBean(NotificationEventPublisher.class);
            assertThat(context.getBean(NotificationEventPublisher.class))
                    .isInstanceOf(KafkaNotificationEventPublisher.class);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KafkaProperties.class)
    static class KafkaPropertiesConfiguration {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeansConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, NotificationEvent> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }
}
