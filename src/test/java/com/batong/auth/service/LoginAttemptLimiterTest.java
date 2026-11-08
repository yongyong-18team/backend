package com.batong.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** TRD 7-1: 5회까지 시도할 수 있고 6번째부터 1분 차단. 성공하면 초기화. 시계를 주입해 대기 없이 확인한다. */
class LoginAttemptLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-07T00:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(now::get);

    @Test
    void 다섯_번은_허용하고_여섯_번째는_차단한다() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    void 차단은_1분_뒤_풀리고_다시_5번_허용한다() {
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        now.set(now.get().plus(Duration.ofSeconds(59)));
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        now.set(now.get().plus(Duration.ofSeconds(2)));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void 성공하면_횟수가_초기화된다() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        limiter.reset("1.1.1.1");
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
    }

    @Test
    void 오래_쉬면_이전_시도_횟수는_잊는다() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        now.set(now.get().plus(Duration.ofMinutes(2)));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
    }

    @Test
    void 오래된_IP_기록은_맵이_커지면_정리된다() {
        for (int i = 0; i < 10_050; i++) {
            limiter.tryAcquire("10.1." + (i / 250) + "." + (i % 250));
        }
        now.set(now.get().plus(Duration.ofMinutes(5)));
        limiter.tryAcquire("9.9.9.9");
        assertThat(limiter.size()).isLessThan(100);
    }
}
