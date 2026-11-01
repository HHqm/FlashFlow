package com.flashflow.trade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfiguration {

    @Bean
    DefaultRedisScript<String> seckillReserveScript() {
        var script = new DefaultRedisScript<String>();
        script.setLocation(new ClassPathResource("lua/seckill_reserve.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    DefaultRedisScript<Long> seckillCompensateScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("lua/seckill_compensate.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    DefaultRedisScript<Long> stockReconcileScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("lua/stock_reconcile.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    DefaultRedisScript<Long> unlockScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("lua/unlock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
