package com.batong.global.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-only-secret-0123456789abcdefghij";

    @Test
    void 발급한_토큰에서_userId를_꺼낸다() {
        JwtProvider provider = new JwtProvider(SECRET, Duration.ofDays(7));

        assertThat(provider.parseUserId(provider.issue(42L))).isEqualTo(42L);
        assertThat(provider.getAccessTtlSeconds()).isEqualTo(604800);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        String token = new JwtProvider("another-test-secret-0123456789abcdefgh", Duration.ofDays(7)).issue(1L);

        assertThatThrownBy(() -> new JwtProvider(SECRET, Duration.ofDays(7)).parseUserId(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_거부한다() {
        JwtProvider provider = new JwtProvider(SECRET, Duration.ofSeconds(-1));

        assertThatThrownBy(() -> provider.parseUserId(provider.issue(1L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 비밀키가_비었거나_32바이트_미만이면_생성되지_않는다() {
        assertThatThrownBy(() -> new JwtProvider("", Duration.ofDays(7))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider(null, Duration.ofDays(7))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider("a".repeat(31), Duration.ofDays(7))).isInstanceOf(IllegalStateException.class);
        new JwtProvider("a".repeat(32), Duration.ofDays(7));
    }
}
