package com.batong.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * REPORT-03 읽기 전용 집계 조회. 주간 리포트는 저장하지 않고(ERD 유도 속성) 승인(APPROVED)된 extracted_item과
 * 설명서 반영 이력(guide_entry)에서 만든다. 일기 원문(content), ai_analysis, evidence_quote는 SELECT하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class WeeklyReportQueryRepository {

    private final JdbcClient jdbc;

    /** 날짜별 일기 수(승인 항목이 하나 이상 있는 일기)와 승인된 BEHAVIOR 항목 수. 기록이 있는 날짜만 나온다 */
    public record DailyCountRow(LocalDate date, int recordCount, int behaviorCount) {
    }

    /** 승인된 BEHAVIOR 항목을 (label, timeSlot)별로 센 값. label·timeSlot은 null일 수 있다 */
    public record BehaviorCountRow(String label, String timeSlot, int count) {
    }

    /** 이 기간에 설명서에 새로 반영된 일기 출처 항목 */
    public record KeyChangeRow(Long diaryId, LocalDate diaryDate, String section, String content) {
    }

    public List<DailyCountRow> findDailyCounts(Long subjectId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT d.diary_date,
                               count(DISTINCT d.diary_id) AS record_count,
                               count(*) FILTER (WHERE i.category = 'BEHAVIOR') AS behavior_count
                        FROM diary d
                        JOIN extracted_item i ON i.diary_id = d.diary_id AND i.status = 'APPROVED'
                        WHERE d.subject_id = :subjectId AND d.diary_date BETWEEN :from AND :to
                        GROUP BY d.diary_date
                        """)
            .param("subjectId", subjectId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new DailyCountRow(
                rs.getObject("diary_date", LocalDate.class),
                rs.getInt("record_count"),
                rs.getInt("behavior_count")))
            .list();
    }

    public List<BehaviorCountRow> findBehaviorCounts(Long subjectId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT i.label, i.time_slot, count(*) AS cnt
                        FROM diary d
                        JOIN extracted_item i ON i.diary_id = d.diary_id AND i.status = 'APPROVED'
                        WHERE d.subject_id = :subjectId AND d.diary_date BETWEEN :from AND :to
                          AND i.category = 'BEHAVIOR'
                        GROUP BY i.label, i.time_slot
                        """)
            .param("subjectId", subjectId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new BehaviorCountRow(
                rs.getString("label"),
                rs.getString("time_slot"),
                rs.getInt("cnt")))
            .list();
    }

    /**
     * 반영 시각(guide_entry.created_at)이 [from 00:00, to+1일 00:00) KST인 DIARY 출처 항목.
     * 출처 일기가 삭제돼 src_diary_id가 NULL이 된 항목은 누를 곳이 없어 제외한다(JOIN)
     */
    public List<KeyChangeRow> findKeyChanges(Long subjectId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT g.src_diary_id, d.diary_date, g.section, g.content
                        FROM guide_entry g
                        JOIN diary d ON d.diary_id = g.src_diary_id AND d.subject_id = g.subject_id
                        WHERE g.subject_id = :subjectId AND g.source_type = 'DIARY'
                          AND g.created_at >= (CAST(:from AS timestamp) AT TIME ZONE 'Asia/Seoul')
                          AND g.created_at < ((CAST(:to AS timestamp) + INTERVAL '1 day') AT TIME ZONE 'Asia/Seoul')
                        ORDER BY g.created_at, g.entry_id
                        """)
            .param("subjectId", subjectId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new KeyChangeRow(
                rs.getLong("src_diary_id"),
                rs.getObject("diary_date", LocalDate.class),
                rs.getString("section"),
                rs.getString("content")))
            .list();
    }

    /** AI-05 입력의 keyChanges에서 이름을 가리기 위한 대상자 이름. 없으면 null */
    public String findSubjectName(Long subjectId) {
        return jdbc.sql("SELECT name FROM care_subject WHERE subject_id = :subjectId")
            .param("subjectId", subjectId)
            .query(String.class)
            .optional()
            .orElse(null);
    }
}
