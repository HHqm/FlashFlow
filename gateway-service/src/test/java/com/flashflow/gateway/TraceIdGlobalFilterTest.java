package com.flashflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import com.flashflow.gateway.config.TraceIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class TraceIdGlobalFilterTest {

    private final TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

    @Test
    void preservesIncomingTraceId() {
        var captured = new AtomicReference<String>();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(TraceIdGlobalFilter.TRACE_ID_HEADER, "trace-123")
                        .build());
        GatewayFilterChain chain = filteredExchange -> {
            captured.set(filteredExchange.getRequest().getHeaders()
                    .getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get()).isEqualTo("trace-123");
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER)).isEqualTo("trace-123");
    }

    @Test
    void createsTraceIdWhenMissing() {
        var captured = new AtomicReference<String>();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        GatewayFilterChain chain = filteredExchange -> {
            captured.set(filteredExchange.getRequest().getHeaders()
                    .getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get()).hasSize(32);
    }
}
