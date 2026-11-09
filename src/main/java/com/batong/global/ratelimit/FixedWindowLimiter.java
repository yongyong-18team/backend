package com.batong.global.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** 키(IP나 사용자)당 1분 고정 창 제한. 인메모리라 서버 한 대 기준이다. */
public class FixedWindowLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int PRUNE_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerMinute;
    private final Supplier<Instant> clock;

    public FixedWindowLimiter(int maxPerMinute) {
        this(maxPerMinute, Instant::now);
    }

    public FixedWindowLimiter(int maxPerMinute, Supplier<Instant> clock) {
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.get();
        if (windows.size() > PRUNE_THRESHOLD) {
            windows.values().removeIf(w -> !now.isBefore(w.start().plus(WINDOW)));
        }
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || !now.isBefore(w.start().plus(WINDOW))) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (w.count() >= maxPerMinute) {
                return w;
            }
            allowed[0] = true;
            return new Window(w.start(), w.count() + 1);
        });
        return allowed[0];
    }
}
