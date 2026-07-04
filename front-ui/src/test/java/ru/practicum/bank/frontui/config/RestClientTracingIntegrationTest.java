package ru.practicum.bank.frontui.config;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0",
        "management.zipkin.tracing.export.enabled=false"
})
@AutoConfigureObservability
class RestClientTracingIntegrationTest {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private Tracer tracer;

    @Test
    void shouldPropagateW3cTraceContext() {
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(request -> assertThat(request.getHeaders().getFirst("traceparent"))
                        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"))
                .andRespond(withSuccess());
        var restClient = restClientBuilder
                .baseUrl("http://localhost")
                .build();
        var span = tracer.nextSpan().name("rest-client-propagation-test").start();

        try (var ignored = tracer.withSpan(span)) {
            restClient.get()
                    .uri("/trace")
                    .retrieve()
                    .toBodilessEntity();
        } finally {
            span.end();
        }

        server.verify();
    }
}
