package com.batong.global.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-ttl}") Duration accessTtl) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // 값은 로그에 남기지 않는다
            throw new IllegalStateException("JWT_SECRET is empty or shorter than 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = accessTtl;
    }

    public String issue(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** 서명·만료가 유효하면 userId, 아니면 JwtException */
    public Long parseUserId(String token) {
        String subject = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new JwtException("invalid subject");
        }
    }

    /** AUTH-03 응답의 expiresIn(초) */
    public long getAccessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
