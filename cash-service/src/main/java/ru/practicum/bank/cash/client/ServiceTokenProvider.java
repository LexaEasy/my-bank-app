package ru.practicum.bank.cash.client;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

@Component
public class ServiceTokenProvider {

    private static final String CLIENT_REGISTRATION_ID = "cash-service";
    private static final String PRINCIPAL_NAME = "cash-service";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public ServiceTokenProvider(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    public String getAccessToken() {
        var authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_REGISTRATION_ID)
                .principal(PRINCIPAL_NAME)
                .build();

        var authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new AccountsClientException("Service token request failed");
        }

        return authorizedClient.getAccessToken().getTokenValue();
    }
}
