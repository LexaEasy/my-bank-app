package ru.practicum.bank.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtTokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    public JwtTokenRelayGatewayFilterFactory() {
        super(Object.class);
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> extractToken(exchange)
                .map(token -> addToken(exchange, token))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private Mono<String> extractToken(ServerWebExchange exchange) {
        var tokenFromContext = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> authentication.getToken().getTokenValue());

        var tokenFromHeader = Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring(7));

        return tokenFromContext.switchIfEmpty(tokenFromHeader);
    }

    private ServerWebExchange addToken(ServerWebExchange exchange, String token) {
        var request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.setBearerAuth(token))
                .build();

        return exchange.mutate()
                .request(request)
                .build();
    }
}
