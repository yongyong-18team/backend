package com.batong.member.service;

import com.batong.global.ratelimit.FixedWindowLimiter;
import org.springframework.stereotype.Component;

/** MEMBER-01 사용자당 분당 10회 제한(API 명세 2-8) */
@Component
public class LookupLimiter extends FixedWindowLimiter {

    public LookupLimiter() {
        super(10);
    }
}
