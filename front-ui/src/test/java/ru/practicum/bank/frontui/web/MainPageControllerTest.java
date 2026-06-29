package ru.practicum.bank.frontui.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.common.dto.exchange.ExchangeRateResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.frontui.client.GatewayClient;
import ru.practicum.bank.frontui.client.GatewayClientException;
import ru.practicum.bank.frontui.dto.AccountForm;
import ru.practicum.bank.frontui.dto.AccountResponse;
import ru.practicum.bank.frontui.dto.CashForm;
import ru.practicum.bank.frontui.dto.CashOperationResponse;
import ru.practicum.bank.frontui.dto.RecipientResponse;
import ru.practicum.bank.frontui.dto.TransferForm;
import ru.practicum.bank.frontui.dto.TransferResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MainPageController.class)
class MainPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayClient gatewayClient;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @BeforeEach
    void setUp() {
        when(gatewayClient.getExchangeRates(anyString())).thenReturn(exchangeRates());
    }

    @Test
    void shouldRenderMainPage() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());

        mockMvc.perform(get("/")
                        .with(user("ivan")))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("accountForm", new AccountForm(
                        "Иванов Иван",
                        LocalDate.parse("1990-01-15")
                )))
                .andExpect(model().attribute("balance", new BigDecimal("1000.00")))
                .andExpect(model().attributeExists("transferForm"))
                .andExpect(model().attribute("recipients", recipients()))
                .andExpect(model().attribute("username", "ivan"))
                .andExpect(content().string(allOf(
                        containsString("Обо мне"),
                        containsString("Операции с наличными"),
                        containsString("Переводы"),
                        containsString("Курсы валют"),
                        containsString("USD"),
                        containsString("90.0000"),
                        containsString("value=\"1990-01-15\""),
                        containsString("Петров Петр (petr)")
                )));
    }

    @Test
    void shouldRenderMainPageWhenExchangeRatesAreUnavailable() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());
        when(gatewayClient.getExchangeRates("user-token"))
                .thenThrow(new GatewayClientException("Exchange service request failed"));

        mockMvc.perform(get("/")
                        .with(user("ivan")))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("exchangeRates", List.of()))
                .andExpect(model().attribute("exchangeRatesLoadError", "Exchange service request failed"))
                .andExpect(content().string(containsString("Exchange service request failed")));
    }

    @Test
    void shouldUsePreferredUsernameFromOidcUser() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("user")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());

        mockMvc.perform(get("/")
                        .with(oidcLogin().idToken(token -> token.claim("preferred_username", "ivan"))))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("username", "ivan"));
    }

    @Test
    void shouldTransferMoney() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());
        when(gatewayClient.transfer(eq("user-token"), eq(new TransferForm(
                "petr",
                new BigDecimal("100.00"),
                "RUB"
        )))).thenReturn(new TransferResponse(
                "ivan",
                "petr",
                new BigDecimal("900.00"),
                "RUB",
                "Transfer completed"
        ));

        mockMvc.perform(post("/transfers")
                        .with(user("ivan"))
                        .with(csrf())
                        .param("recipientLogin", "petr")
                        .param("amount", "100.00")
                        .param("currency", "RUB")
                        .param("sourceCurrency", "RUB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Перевод выполнен"))
                .andExpect(flash().attributeExists("transferResponse"));
    }

    @Test
    void shouldShowTransferError() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());
        when(gatewayClient.transfer(eq("user-token"), eq(new TransferForm(
                "petr",
                new BigDecimal("1500.00"),
                "RUB"
        )))).thenThrow(new GatewayClientException("Недостаточно средств"));

        mockMvc.perform(post("/transfers")
                        .with(user("ivan"))
                        .with(csrf())
                        .param("recipientLogin", "petr")
                        .param("amount", "1500.00")
                        .param("currency", "RUB")
                        .param("sourceCurrency", "RUB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", "Недостаточно средств"));
    }

    @Test
    void shouldUpdateAccount() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.updateAccount(eq("user-token"), eq(new AccountForm(
                "Иван Петров",
                LocalDate.parse("1990-01-15")
        )))).thenReturn(account("ivan", "Иван Петров", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());

        mockMvc.perform(post("/account")
                        .with(user("ivan"))
                        .with(csrf())
                        .param("name", "Иван Петров")
                        .param("birthdate", "1990-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Данные аккаунта сохранены"));
    }

    @Test
    void shouldDepositCash() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());
        when(gatewayClient.deposit(eq("user-token"), eq(new CashForm(
                new BigDecimal("250.00"),
                "RUB"
        )))).thenReturn(new CashOperationResponse(
                new BigDecimal("1250.00"),
                "RUB",
                "Cash deposited"
        ));

        mockMvc.perform(post("/cash")
                        .with(user("ivan"))
                        .with(csrf())
                        .param("amount", "250.00")
                        .param("currency", "RUB")
                        .param("action", "deposit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Cash deposited"));
    }

    @Test
    void shouldWithdrawCash() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
        when(gatewayClient.getAccount("user-token"))
                .thenReturn(account("ivan", "Иванов Иван", LocalDate.parse("1990-01-15"), "1000.00"));
        when(gatewayClient.getRecipients("user-token"))
                .thenReturn(recipients());
        when(gatewayClient.withdraw(eq("user-token"), eq(new CashForm(
                new BigDecimal("100.00"),
                "RUB"
        )))).thenReturn(new CashOperationResponse(
                new BigDecimal("900.00"),
                "RUB",
                "Cash withdrawn"
        ));

        mockMvc.perform(post("/cash")
                        .with(user("ivan"))
                        .with(csrf())
                        .param("amount", "100.00")
                        .param("currency", "RUB")
                        .param("action", "withdraw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Cash withdrawn"));
    }

    private OAuth2AuthorizedClient authorizedClient(String tokenValue) {
        var clientRegistration = ClientRegistration.withRegistrationId("front-ui")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("http://localhost:8180/realms/bank-realm/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8180/realms/bank-realm/protocol/openid-connect/token")
                .clientId("front-ui")
                .clientSecret("")
                .build();
        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.parse("2026-06-13T00:00:00Z"),
                Instant.parse("2026-06-13T01:00:00Z")
        );

        return new OAuth2AuthorizedClient(clientRegistration, "ivan", accessToken);
    }

    private AccountResponse account(String login, String name, LocalDate birthdate, String balance) {
        return new AccountResponse(
                login,
                name,
                birthdate,
                new BigDecimal(balance),
                "RUB"
        );
    }

    private List<RecipientResponse> recipients() {
        return List.of(
                new RecipientResponse("petr", "Петров Петр"),
                new RecipientResponse("anna", "Анна Смирнова")
        );
    }

    private List<ExchangeRateResponse> exchangeRates() {
        return List.of(
                new ExchangeRateResponse(
                        Currency.RUB,
                        new BigDecimal("1.0000"),
                        new BigDecimal("1.0000"),
                        Instant.parse("2026-06-25T10:00:00Z")
                ),
                new ExchangeRateResponse(
                        Currency.USD,
                        new BigDecimal("90.0000"),
                        new BigDecimal("92.0000"),
                        Instant.parse("2026-06-25T10:00:00Z")
                )
        );
    }
}
