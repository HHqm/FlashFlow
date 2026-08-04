package com.flashflow.trade.order;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class OrderNumberGenerator {

    public String next() {
        return "FF" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
