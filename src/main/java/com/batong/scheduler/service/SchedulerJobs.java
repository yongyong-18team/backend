package com.batong.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 스케줄 트리거(TRD 12-6). 시각은 한국 시간(KST) 기준이고, 실제 작업은 {@link SchedulerService}가 한다. */
@Component
@RequiredArgsConstructor
public class SchedulerJobs {

    private static final String KST = "Asia/Seoul";

    private final SchedulerService service;

    /** 공유 초안 정리: 매일 03:00 */
    @Scheduled(cron = "0 0 3 * * *", zone = KST)
    public void purgeStaleDrafts() {
        service.purgeStaleDrafts(Instant.now());
    }

    /** 고정본 파기: 매일 03:10 */
    @Scheduled(cron = "0 10 3 * * *", zone = KST)
    public void clearExpiredSnapshots() {
        service.clearExpiredSnapshots(Instant.now());
    }

    /** 분석 상태 복구: 5분마다 */
    @Scheduled(cron = "0 */5 * * * *", zone = KST)
    public void recoverStuckAnalyses() {
        service.recoverStuckAnalyses(Instant.now());
    }
}
