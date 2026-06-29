package ru.practicum.bank.exchangegenerator.client;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceTokenProviderTest {

    private final OAuth2AuthorizedClientManager authorizedClientManager = mock(OAuth2AuthorizedClientManager.class);
    private final ServiceTokenProvider serviceTokenProvider = new ServiceTokenProvider(authorizedClientManager);

    @Test
    void shouldReturnServiceAccessToken() {
        when(authorizedClientManager.authorize(argThat(request ->
                "exchange-generator".equals(request.getClientRegistrationId())
                        && "exchange-generator".equals(request.getPrincipal().getName())
        ))).thenReturn(authorizedClient("service-token"));

        assertThat(serviceTokenProvider.getAccessToken()).isEqualTo("service-token");
    }

    @Test
    void shouldThrowWhenTokenWasNotIssued() {
        when(authorizedClientManager.authorize(argThat(request ->
                "exchange-generator".equals(request.getClientRegistrationId())
        ))).thenReturn(null);

        assertThatThrownBy(serviceTokenProvider::getAccessToken)
                .isInstanceOf(ExchangeClientException.class)
                .hasMessage("Service token request failed");
    }

    private OAuth2AuthorizedClient authorizedClient(String tokenValue) {
        var clientRegistration = ClientRegistration.withRegistrationId("exchange-generator")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:8180/realms/bank-realm/protocol/openid-connect/token")
                .clientId("exchange-generator")
                .clientSecret("")
                .build();
        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.parse("2026-06-13T00:00:00Z"),
                Instant.parse("2026-06-13T01:00:00Z")
        );

        return new OAuth2AuthorizedClient(clientRegistration, "exchange-generator", accessToken);
    }
}
