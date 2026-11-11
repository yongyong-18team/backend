package com.batong.scheduler.repository;

import com.batong.global.enums.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 스케줄러(TRD 12-6) 전용 쓰기 쿼리. share·diary 패키지에 의존하지 않도록 JdbcClient로 직접 쓴다.
 * 세 쿼리 모두 한 문장이라 한 번에 끝나고, 두 번 실행돼도 결과가 같다.
 * 일기 본문(content)과 공유 토큰은 읽지도 남기지도 않는다.
 */
@Repository
@RequiredArgsConstructor
public class SchedulerQueryRepository {

    private final JdbcClient jdbc;

    /**
     * 승인되지 않은 공유 초안 중 {@code createdBefore} 이전에 만든 것을 지운다(ERD: 생성 1일 후 삭제).
     * share_section은 FK ON DELETE CASCADE로 같이 지워진다.
     *
     * @return 지운 행 수
     */
    public int deleteDraftsCreatedBefore(Instant createdBefore) {
        return jdbc.sql("DELETE FROM share WHERE approved_at IS NULL AND created_at < :cutoff")
            .param("cutoff", Timestamp.from(createdBefore))
            .update();
    }

    /**
     * 만료 시각이 {@code expiredBefore} 이전인 share의 고정본(snapshot_json)을 비운다. 행은 남긴다.
     * 초안은 expires_at이 NULL이라 비교에 걸리지 않는다.
     *
     * @return 비운 행 수
     */
    public int clearSnapshotsExpiredBefore(Instant expiredBefore) {
        return jdbc.sql("""
                        UPDATE share SET snapshot_json = NULL
                        WHERE snapshot_json IS NOT NULL AND expires_at < :cutoff""")
            .param("cutoff", Timestamp.from(expiredBefore))
            .update();
    }

    /**
     * {@code startedBefore} 이전부터 PROCESSING인 일기를 FAILED로 바꾼다. 일기는 그대로 두고 다시 분석할 수 있다.
     * PROCESSING 시작 시각은 updated_at이다(DiaryRepository.startAnalysis가 갱신한다).
     * 분석 시작·삭제와 같은 행을 다투는 조건부 UPDATE라 동시에 와도 한쪽만 적용된다.
     *
     * @return 바꾼 행 수
     */
    public int failAnalysesProcessingSince(Instant startedBefore, Instant now) {
        return jdbc.sql("""
                        UPDATE diary SET analysis_status = :failed, updated_at = :now
                        WHERE analysis_status = :processing AND updated_at < :cutoff""")
            .param("failed", AnalysisStatus.FAILED.name())
            .param("processing", AnalysisStatus.PROCESSING.name())
            .param("now", Timestamp.from(now))
            .param("cutoff", Timestamp.from(startedBefore))
            .update();
    }
}
