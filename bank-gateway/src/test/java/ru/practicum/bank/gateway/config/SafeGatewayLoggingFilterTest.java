package ru.practicum.bank.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SafeGatewayLoggingFilterTest {

    private final SafeGatewayLoggingFilter filter = new SafeGatewayLoggingFilter();

    @Test
    void shouldLogSuccessfulRelayWithoutSensitiveHeaders(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route("accounts-service"));

        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.OK);
            return filteredExchange.getResponse().setComplete();
        }).block();

        assertThat(output)
                .contains("Gateway request relayed")
                .contains("routeId=accounts-service")
                .contains("method=GET")
                .contains("path=/api/accounts/me")
                .contains("targetService=accounts-service")
                .contains("status=200")
                .doesNotContain("user-token")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("password")
                .doesNotContain("client_secret");
    }

    @Test
    void shouldWarnWhenPublicRouteTargetsInternalApi(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/accounts/internal/balance")
                .build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route("block-accounts-internal-api"));

        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return filteredExchange.getResponse().setComplete();
        }).block();

        assertThat(output)
                .contains("Gateway internal api access rejected")
                .contains("routeId=block-accounts-internal-api")
                .contains("status=404")
                .contains("errorCode=INTERNAL_API_PUBLIC_ACCESS");
    }

    private Route route(String routeId) {
        return Route.async()
                .id(routeId)
                .uri(URI.create("http://" + routeId))
                .predicate(exchange -> true)
                .build();
    }
}
