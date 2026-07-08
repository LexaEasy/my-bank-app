package ru.practicum.bank.transfer.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TransferFailureMetricsFilter extends OncePerRequestFilter {

    static final String METRIC_NAME = "bank.transfer.failures";
    private static final String TRANSFER_PATH = "/api/transfers";
    private static final String UNKNOWN_RECIPIENT = "unknown";
    private static final int REQUEST_CACHE_LIMIT = 16_384;

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public TransferFailureMetricsFilter(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isTransfer(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        var cachedRequest = new ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT);
        try {
            filterChain.doFilter(cachedRequest, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            recordFailure(cachedRequest);
            throw exception;
        }

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            recordFailure(cachedRequest);
        }
    }

    private boolean isTransfer(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && TRANSFER_PATH.equals(request.getRequestURI());
    }

    private void recordFailure(ContentCachingRequestWrapper request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return;
        }

        String senderLogin = jwtAuthentication.getToken().getClaimAsString("preferred_username");
        if (senderLogin == null || senderLogin.isBlank()) {
            return;
        }

        meterRegistry.counter(
                METRIC_NAME,
                "sender_login", senderLogin,
                "recipient_login", recipientLogin(request)
        ).increment();
    }

    private String recipientLogin(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return UNKNOWN_RECIPIENT;
        }

        try {
            String recipient = objectMapper.readTree(content).path("recipientLogin").asText();
            return recipient == null || recipient.isBlank() ? UNKNOWN_RECIPIENT : recipient;
        } catch (IOException exception) {
            return UNKNOWN_RECIPIENT;
        }
    }
}
