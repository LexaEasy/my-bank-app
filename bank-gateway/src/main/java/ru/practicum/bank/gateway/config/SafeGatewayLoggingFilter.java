package ru.practicum.bank.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SafeGatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SafeGatewayLoggingFilter.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public reactor.core.publisher.Mono<Void> filter(
            org.springframework.web.server.ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain
    ) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unmatched" : route.getId();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway route selected routeId={} method={} path={} source=bank-gateway targetService={}",
                    routeId,
                    method,
                    path,
                    targetService(routeId)
            );
        }

        return chain.filter(exchange)
                .doOnSuccess(ignored -> logCompleted(exchange, routeId, method, path, null))
                .doOnError(error -> logCompleted(exchange, routeId, method, path, error));
    }

    private void logCompleted(
            org.springframework.web.server.ServerWebExchange exchange,
            String routeId,
            String method,
            String path,
            Throwable error
    ) {
        int status = exchange.getResponse().getStatusCode() == null
                ? HttpStatus.OK.value()
                : exchange.getResponse().getStatusCode().value();
        String targetService = targetService(routeId);

        if (error != null || status >= 500) {
            log.error(
                    "Gateway request failed routeId={} method={} path={} targetService={} status={} errorCategory=backend_failure errorType={} source=bank-gateway",
                    routeId,
                    method,
                    path,
                    targetService,
                    status,
                    error == null ? "Backend5xx" : error.getClass().getSimpleName()
            );
            return;
        }

        if ("block-accounts-internal-api".equals(routeId)) {
            log.warn(
                    "Gateway internal api access rejected routeId={} method={} path={} targetService={} status={} errorCode=INTERNAL_API_PUBLIC_ACCESS source=bank-gateway",
                    routeId,
                    method,
                    path,
                    targetService,
                    status
            );
            return;
        }

        if (status >= 400) {
            log.warn(
                    "Gateway backend request rejected routeId={} method={} path={} targetService={} status={} errorCategory=backend_4xx source=bank-gateway",
                    routeId,
                    method,
                    path,
                    targetService,
                    status
            );
            return;
        }

        log.info(
                "Gateway request relayed routeId={} method={} path={} targetService={} status={} source=bank-gateway",
                routeId,
                method,
                path,
                targetService,
                status
        );
    }

    private String targetService(String routeId) {
        return switch (routeId) {
            case "accounts-service" -> "accounts-service";
            case "cash-service" -> "cash-service";
            case "transfer-service" -> "transfer-service";
            case "exchange-service" -> "exchange-service";
            case "block-accounts-internal-api" -> "accounts-service";
            default -> "unknown";
        };
    }
}
