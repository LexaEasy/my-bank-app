package ru.practicum.bank.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenRelayGatewayFilterFactoryTest {

    private final JwtTokenRelayGatewayFilterFactory factory = new JwtTokenRelayGatewayFilterFactory();

    @Test
    void shouldRelayBearerTokenFromAuthorizationHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .build());
        var relayedHeader = new AtomicReference<String>();

        factory.apply(new Object())
                .filter(exchange, filteredExchange -> {
                    relayedHeader.set(filteredExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                    return filteredExchange.getResponse().setComplete();
                })
                .block();

        assertThat(relayedHeader).hasValue("Bearer user-token");
    }
}
