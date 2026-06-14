package ru.practicum.bank.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRouteConfigTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void shouldRouteTransfersToTransferServiceWithTokenRelay() {
        var routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes)
                .filteredOn(route -> "transfer-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getUri().toString()).isEqualTo("lb://transfer-service");
                    assertThat(route.getPredicates())
                            .anySatisfy(predicate -> assertThat(predicate.getArgs())
                                    .containsValue("/api/transfers")
                                    .containsValue("/api/transfers/**"));
                    assertThat(route.getFilters())
                            .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("JwtTokenRelay"));
                });
    }
}
