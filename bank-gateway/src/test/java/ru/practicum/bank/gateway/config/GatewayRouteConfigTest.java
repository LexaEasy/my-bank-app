package ru.practicum.bank.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRouteConfigTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void shouldRouteBackendRequestsToServiceDnsTargetsWithTokenRelay() {
        var routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        Map<String, String> expectedUris = Map.of(
                "accounts-service", "http://accounts-service:8081",
                "cash-service", "http://cash-service:8082",
                "transfer-service", "http://transfer-service:8083",
                "exchange-service", "http://exchange-service:8086"
        );

        expectedUris.forEach((routeId, uri) -> assertThat(routes)
                .filteredOn(route -> routeId.equals(route.getId()))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getUri().toString()).isEqualTo(uri);
                    assertThat(route.getUri().getScheme()).isEqualTo("http");
                    assertThat(route.getFilters())
                            .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("JwtTokenRelay"));
                }));
    }

    @Test
    void shouldKeepTransferRouteExactAndBlockAccountsInternalApi() {
        var routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes)
                .filteredOn(route -> "transfer-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> assertThat(route.getPredicates())
                        .anySatisfy(predicate -> assertThat(predicate.getArgs())
                                .containsValue("/api/transfers")
                                .doesNotContainValue("/api/transfers/**")));

        assertThat(routes)
                .filteredOn(route -> "block-accounts-internal-api".equals(route.getId()))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getUri().toString()).isEqualTo("no://op");
                    assertThat(route.getPredicates())
                            .anySatisfy(predicate -> assertThat(predicate.getArgs())
                                    .containsValue("/api/accounts/internal/**"));
                    assertThat(route.getFilters())
                            .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("SetStatus"));
                });
    }
}
