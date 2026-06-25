package ru.practicum.bank.exchangegenerator.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;
import ru.practicum.bank.exchangegenerator.client.ExchangeClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExchangeRatePublisherTest {

    private final ExchangeRateGenerator generator = new ExchangeRateGenerator();
    private final ExchangeClient exchangeClient = mock(ExchangeClient.class);
    private final ExchangeRatePublisher publisher = new ExchangeRatePublisher(generator, exchangeClient);

    @Test
    void shouldPublishNextGeneratedRates() {
        publisher.publishNextRates();

        var captor = ArgumentCaptor.forClass(ExchangeRatesUpdateRequest.class);
        verify(exchangeClient).updateRates(captor.capture());

        assertThat(captor.getValue().rates())
                .extracting(rate -> rate.currency().name())
                .containsExactly("RUB", "USD", "CNY");
        assertThat(captor.getValue().rates().get(1).buyRate()).isEqualByComparingTo("90.0000");
    }
}
