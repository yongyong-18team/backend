package com.batong.scheduler.service;

import com.batong.scheduler.repository.SchedulerQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 정리 작업 3개의 로직(TRD 12-6). 기준 시각을 받아서 시간을 고정해 테스트할 수 있다.
 * 로그에는 처리한 건수만 남긴다(일기 본문·토큰 금지, TRD 13장).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    /** 승인 안 된 공유 초안을 남겨 두는 시간 */
    static final Duration DRAFT_TTL = Duration.ofDays(1);
    /** 만료된 공유 고정본을 남겨 두는 시간(NFR-11) */
    static final Duration SNAPSHOT_RETENTION = Duration.ofDays(30);
    /** 이 시간을 넘겨 PROCESSING이면 멈춘 것으로 본다 */
    static final Duration ANALYSIS_TIMEOUT = Duration.ofMinutes(2);

    private final SchedulerQueryRepository queries;

    /** 승인 안 된 지 1일 지난 공유 초안을 삭제한다 */
    public int purgeStaleDrafts(Instant now) {
        int deleted = queries.deleteDraftsCreatedBefore(now.minus(DRAFT_TTL));
        if (deleted > 0) {
            log.info("공유 초안 정리: {}건 삭제", deleted);
        }
        return deleted;
    }

    /** 만료 30일 지난 공유의 고정본(snapshot_json)을 비운다 */
    public int clearExpiredSnapshots(Instant now) {
        int cleared = queries.clearSnapshotsExpiredBefore(now.minus(SNAPSHOT_RETENTION));
        if (cleared > 0) {
            log.info("공유 고정본 파기: {}건", cleared);
        }
        return cleared;
    }

    /** 2분 넘게 PROCESSING인 일기를 FAILED로 되돌려 다시 분석하거나 지울 수 있게 한다 */
    public int recoverStuckAnalyses(Instant now) {
        int recovered = queries.failAnalysesProcessingSince(now.minus(ANALYSIS_TIMEOUT), now);
        if (recovered > 0) {
            log.info("분석 상태 복구: {}건을 FAILED로 변경", recovered);
        }
        return recovered;
    }
}
