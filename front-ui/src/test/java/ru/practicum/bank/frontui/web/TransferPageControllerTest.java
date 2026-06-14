package ru.practicum.bank.frontui.web;

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
import ru.practicum.bank.frontui.client.GatewayClient;
import ru.practicum.bank.frontui.dto.TransferForm;
import ru.practicum.bank.frontui.dto.TransferResponse;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(TransferPageController.class)
class TransferPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayClient gatewayClient;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @Test
    void shouldTransferMoney() throws Exception {
        var authorizedClient = authorizedClient("user-token");
        when(authorizedClientService.loadAuthorizedClient(eq("front-ui"), eq("ivan")))
                .thenReturn(authorizedClient);
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
                        .param("currency", "RUB"))
                .andExpect(status().isOk())
                .andExpect(view().name("transfers"))
                .andExpect(model().attribute("successMessage", "Перевод выполнен"))
                .andExpect(model().attributeExists("transferResponse"));
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
}
