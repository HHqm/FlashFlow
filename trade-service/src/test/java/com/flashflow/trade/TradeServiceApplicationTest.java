package com.flashflow.trade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trade_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.admin.fail-fast=false",
        "flashflow.demo-data-enabled=false",
        "flashflow.jobs.outbox-enabled=false",
        "flashflow.jobs.order-timeout-enabled=false"
})
class TradeServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}
