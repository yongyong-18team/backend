package com.batong.auth.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * TRD 7-1: 로그인 5회 실패 시 해당 IP 1분 제한.
 * 비밀번호 검증(BCrypt, 수백 ms) 전에 시도를 먼저 센다. 검증 뒤에 세면 동시에 보낸 요청이 모두 통과한다.
 * 성공하면 {@link #reset}으로 지운다.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration BLOCK = Duration.ofMinutes(1);
    private static final int PRUNE_THRESHOLD = 10_000;

    private record State(int attempts, Instant touchedAt, Instant blockedUntil) {
    }

    // ponytail: 서버 한 대 메모리 맵. 인스턴스를 늘리면 DB·Redis로 옮긴다
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public LoginAttemptLimiter() {
        this(Instant::now);
    }

    LoginAttemptLimiter(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** 시도해도 되면 true. 6번째 시도부터 BLOCK 동안 false. 1분 넘게 쉬면 횟수를 잊는다. */
    public boolean tryAcquire(String ip) {
        Instant now = clock.get();
        if (states.size() > PRUNE_THRESHOLD) {
            states.values().removeIf(s -> now.isAfter(s.touchedAt().plus(BLOCK))
                    && (s.blockedUntil() == null || now.isAfter(s.blockedUntil())));
        }
        boolean[] allowed = {true};
        states.compute(ip, (k, s) -> {
            if (s != null && s.blockedUntil() != null) {
                if (now.isBefore(s.blockedUntil())) {
                    allowed[0] = false;
                    return s;
                }
                s = null;
            }
            if (s != null && now.isAfter(s.touchedAt().plus(BLOCK))) {
                s = null;
            }
            int attempts = (s == null ? 0 : s.attempts()) + 1;
            if (attempts > MAX_ATTEMPTS) {
                allowed[0] = false;
                return new State(0, now, now.plus(BLOCK));
            }
            return new State(attempts, now, null);
        });
        return allowed[0];
    }

    public void reset(String ip) {
        states.remove(ip);
    }

    int size() {
        return states.size();
    }
}
