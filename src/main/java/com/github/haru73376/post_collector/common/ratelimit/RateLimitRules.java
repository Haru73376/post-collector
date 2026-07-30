package com.github.haru73376.post_collector.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;

import java.time.Duration;

public final class RateLimitRules {

    // 5 attempts/minute per client IP, to slow down password brute-forcing
    public static final Bandwidth LOGIN = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
    // 3 attempts/minute per client IP, to slow down automated mass account creation
    public static final Bandwidth REGISTER = Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));
    // 60 requests/minute per authenticated user, as a general abuse/overuse guard
    public static final Bandwidth AUTHENTICATED_API = Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1)));

    private RateLimitRules() {
    }
}
