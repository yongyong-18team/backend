package com.batong.auth.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** TRD 7-1: 로그인 5회 실패 시 해당 IP 1분 제한. 성공하면 카운터를 지운다. */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK = Duration.ofMinutes(1);

    private record State(int failures, Instant blockedUntil) {
    }

    // ponytail: 서버 한 대 메모리 맵. 인스턴스를 늘리면 DB·Redis로 옮긴다
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        State s = states.get(ip);
        if (s == null || s.blockedUntil() == null) {
            return false;
        }
        if (Instant.now().isBefore(s.blockedUntil())) {
            return true;
        }
        states.remove(ip, s);
        return false;
    }

    public void recordFailure(String ip) {
        states.merge(ip, new State(1, null), (old, ignored) -> {
            int failures = old.failures() + 1;
            return failures >= MAX_FAILURES ? new State(0, Instant.now().plus(BLOCK)) : new State(failures, null);
        });
    }

    public void reset(String ip) {
        states.remove(ip);
    }
}
