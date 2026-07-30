package com.github.haru73376.post_collector.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

// Bucket state lives only in this process's memory, so counts reset on restart and aren't
// shared across instances if this app is ever scaled horizontally. Fine for a single-instance
// deployment; would need a shared store (e.g. Redis) to stay correct across multiple instances.
@Component
public class RateLimiterRegistry {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, Bandwidth rule) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(rule).build());
        return bucket.tryConsume(1);
    }
}
