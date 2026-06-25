package ru.practicum.bank.exchangegenerator.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.practicum.bank.exchangegenerator.client.ExchangeClient;

@Service
public class ExchangeRatePublisher {

    private final ExchangeRateGenerator exchangeRateGenerator;
    private final ExchangeClient exchangeClient;

    public ExchangeRatePublisher(ExchangeRateGenerator exchangeRateGenerator, ExchangeClient exchangeClient) {
        this.exchangeRateGenerator = exchangeRateGenerator;
        this.exchangeClient = exchangeClient;
    }

    @Scheduled(fixedDelayString = "${bank.exchange-generator.fixed-delay-ms:1000}")
    public void publishNextRates() {
        exchangeClient.updateRates(exchangeRateGenerator.nextRates());
    }
}
