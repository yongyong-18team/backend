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

    private static String b64(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void 서명_없는_alg_none_토큰은_거부한다() {
        JwtProvider provider = new JwtProvider(SECRET, Duration.ofDays(7));
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = b64("{\"alg\":\"none\"}") + "." + b64("{\"sub\":\"1\",\"exp\":" + exp + "}") + ".";

        assertThatThrownBy(() -> provider.parseUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 서명이_바뀐_토큰은_거부한다() {
        JwtProvider provider = new JwtProvider(SECRET, Duration.ofDays(7));
        String token = provider.issue(1L);
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThatThrownBy(() -> provider.parseUserId(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void 본문의_userId를_바꾸면_서명이_맞지_않아_거부한다() {
        JwtProvider provider = new JwtProvider(SECRET, Duration.ofDays(7));
        String[] parts = provider.issue(1L).split("\\.");
        String forged = parts[0] + "." + b64("{\"sub\":\"2\",\"exp\":9999999999}") + "." + parts[2];

        assertThatThrownBy(() -> provider.parseUserId(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void 서명은_맞아도_subject가_숫자가_아니면_거부한다() {
        JwtProvider provider = new JwtProvider(SECRET, Duration.ofDays(7));
        String token = io.jsonwebtoken.Jwts.builder().subject("abc")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> provider.parseUserId(token)).isInstanceOf(JwtException.class);
    }
}
