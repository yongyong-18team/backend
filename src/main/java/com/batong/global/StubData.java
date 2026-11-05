package com.batong.global;

import java.time.Instant;
import java.time.OffsetDateTime;

/** API 스텁 단계에서 명세 예시 값을 만들 때만 쓴다. 실제 구현으로 바꾸면 사용처와 함께 지운다. */
public final class StubData {

    private StubData() {
    }

    /** 명세 예시의 "2026-11-03T21:30:00+09:00" 같은 문자열 → Instant */
    public static Instant at(String isoOffset) {
        return OffsetDateTime.parse(isoOffset).toInstant();
    }
}
