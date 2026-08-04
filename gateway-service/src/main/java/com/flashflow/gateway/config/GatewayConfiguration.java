package com.flashflow.gateway.config;

import java.security.Principal;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfiguration {

    @Bean
    KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var remoteAddress = exchange.getRequest().getRemoteAddress();
                    return remoteAddress == null
                            ? "anonymous"
                            : remoteAddress.getAddress().getHostAddress();
                }));
    }
}
