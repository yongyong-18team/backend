package com.batong.global.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowLimiterTest {

    @Test
    void IP당_분당_30회까지만_허용하고_1분_뒤_다시_허용한다() {
        var now = new AtomicReference<>(Instant.parse("2026-11-08T00:00:00Z"));
        var limiter = new FixedWindowLimiter(30, now::get);
        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).as("%d번째", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
        now.set(now.get().plus(Duration.ofSeconds(59)));
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        now.set(now.get().plus(Duration.ofSeconds(2)));
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }
}
