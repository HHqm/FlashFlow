package com.flashflow.trade.common;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisDistributedLock(
            StringRedisTemplate redisTemplate,
            DefaultRedisScript<Long> unlockScript
    ) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = unlockScript;
    }

    public Optional<LockToken> tryLock(String key, Duration ttl) {
        String value = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(acquired)
                ? Optional.of(new LockToken(key, value))
                : Optional.empty();
    }

    public void unlock(LockToken token) {
        redisTemplate.execute(unlockScript, List.of(token.key()), token.value());
    }

    public record LockToken(String key, String value) {
    }
}
