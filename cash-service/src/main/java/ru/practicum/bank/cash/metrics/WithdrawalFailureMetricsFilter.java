package ru.practicum.bank.cash.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WithdrawalFailureMetricsFilter extends OncePerRequestFilter {

    static final String METRIC_NAME = "bank.cash.withdrawal.failures";
    private static final String WITHDRAWAL_PATH = "/api/cash/withdraw";

    private final MeterRegistry meterRegistry;

    public WithdrawalFailureMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isWithdrawal(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            recordFailure();
            throw exception;
        }

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            recordFailure();
        }
    }

    private boolean isWithdrawal(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && WITHDRAWAL_PATH.equals(request.getRequestURI());
    }

    private void recordFailure() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return;
        }

        String login = jwtAuthentication.getToken().getClaimAsString("preferred_username");
        if (login == null || login.isBlank()) {
            return;
        }

        meterRegistry.counter(METRIC_NAME, "login", login).increment();
    }
}
