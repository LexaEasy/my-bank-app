package ru.practicum.bank.cash.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalFailureMetricsFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WithdrawalFailureMetricsFilter filter = new WithdrawalFailureMetricsFilter(meterRegistry);

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("preferred_username", "ivan")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        meterRegistry.clear();
    }

    @Test
    void shouldCountFinalNon2xxResponseOnceAfterInternalRetries() throws Exception {
        var attempts = new AtomicInteger();
        var request = withdrawalRequest();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, servletResponse) -> {
            attempts.addAndGet(3);
            ((MockHttpServletResponse) servletResponse).setStatus(422);
        });

        assertThat(attempts).hasValue(3);
        assertThat(failureCount()).isEqualTo(1);
    }

    @Test
    void shouldNotCountSuccessfulWithdrawal() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(withdrawalRequest(), response, (ignoredRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(200));

        assertThat(failureCount()).isZero();
    }

    @Test
    void shouldCountUnhandledFailureOnce() {
        assertThatThrownBy(() -> filter.doFilter(
                withdrawalRequest(),
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> {
                    throw new ServletException("failure");
                }
        )).isInstanceOf(ServletException.class);

        assertThat(failureCount()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreDepositAndUnauthenticatedWithdrawal() throws IOException, ServletException {
        var deposit = new MockHttpServletRequest("POST", "/api/cash/deposit");
        var response = new MockHttpServletResponse();
        filter.doFilter(deposit, response, (ignoredRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(400));

        SecurityContextHolder.clearContext();
        filter.doFilter(withdrawalRequest(), response, (ignoredRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(400));

        assertThat(failureCount()).isZero();
    }

    private MockHttpServletRequest withdrawalRequest() {
        return new MockHttpServletRequest("POST", "/api/cash/withdraw");
    }

    private double failureCount() {
        var counter = meterRegistry.find(WithdrawalFailureMetricsFilter.METRIC_NAME)
                .tag("login", "ivan")
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
