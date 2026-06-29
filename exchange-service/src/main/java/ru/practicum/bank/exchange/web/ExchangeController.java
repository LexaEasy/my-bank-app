package ru.practicum.bank.exchange.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.dto.exchange.ExchangeRateResponse;
import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.exchange.service.ExchangeService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/exchange")
public class ExchangeController {

    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/rates")
    public List<ExchangeRateResponse> getRates() {
        return exchangeService.getRates();
    }

    @PutMapping("/rates")
    public List<ExchangeRateResponse> updateRates(@Valid @RequestBody ExchangeRatesUpdateRequest request) {
        return exchangeService.updateRates(request);
    }

    @GetMapping("/conversion")
    public ConversionResponse convert(
            @RequestParam Currency sourceCurrency,
            @RequestParam Currency targetCurrency,
            @RequestParam BigDecimal amount
    ) {
        return exchangeService.convert(sourceCurrency, targetCurrency, amount);
    }
}
