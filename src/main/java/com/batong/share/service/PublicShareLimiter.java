package com.batong.share.service;

import com.batong.global.ratelimit.FixedWindowLimiter;
import org.springframework.stereotype.Component;

/** PUB-01 IP당 분당 30회 제한(TRD 7-3) */
@Component
public class PublicShareLimiter extends FixedWindowLimiter {

    public PublicShareLimiter() {
        super(30);
    }
}
