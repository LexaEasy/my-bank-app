package ru.practicum.bank.transfer.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TransferFailureMetricsFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TransferFailureMetricsFilter filter =
            new TransferFailureMetricsFilter(meterRegistry, new ObjectMapper());

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("preferred_username", "ivan")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        meterRegistry.clear();
    }

    @Test
    void shouldCountFinalFailureOnceWithContractTags() throws Exception {
        var attempts = new AtomicInteger();
        var response = new MockHttpServletResponse();

        filter.doFilter(transferRequest("{\"recipientLogin\":\"petr\"}"), response, (request, servletResponse) -> {
            request.getInputStream().readAllBytes();
            attempts.addAndGet(3);
            ((MockHttpServletResponse) servletResponse).setStatus(422);
        });

        assertThat(attempts).hasValue(3);
        assertThat(failureCount("petr")).isEqualTo(1);
    }

    @Test
    void shouldUseUnknownForInvalidRecipient() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(transferRequest("{invalid-json"), response, (request, servletResponse) -> {
            request.getInputStream().readAllBytes();
            ((MockHttpServletResponse) servletResponse).setStatus(400);
        });

        assertThat(failureCount("unknown")).isEqualTo(1);
    }

    @Test
    void shouldNotCountSuccessOrUnauthenticatedFailure() throws IOException, ServletException {
        var response = new MockHttpServletResponse();
        filter.doFilter(transferRequest("{\"recipientLogin\":\"petr\"}"), response, (request, servletResponse) -> {
            request.getInputStream().readAllBytes();
            ((MockHttpServletResponse) servletResponse).setStatus(200);
        });

        SecurityContextHolder.clearContext();
        filter.doFilter(transferRequest("{\"recipientLogin\":\"petr\"}"), response, (request, servletResponse) -> {
            request.getInputStream().readAllBytes();
            ((MockHttpServletResponse) servletResponse).setStatus(400);
        });

        assertThat(failureCount("petr")).isZero();
    }

    private MockHttpServletRequest transferRequest(String body) {
        var request = new MockHttpServletRequest("POST", "/api/transfers");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private double failureCount(String recipientLogin) {
        var counter = meterRegistry.find(TransferFailureMetricsFilter.METRIC_NAME)
                .tag("sender_login", "ivan")
                .tag("recipient_login", recipientLogin)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
