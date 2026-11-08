package com.batong.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * REPORT-01·02 읽기 전용 조회. 일일 기록은 저장하지 않고(ERD 유도 속성) diary와 승인(APPROVED)된 extracted_item에서 만든다.
 * 일기 원문(content), ai_analysis(공감 문장·raw_json), evidence_quote는 SELECT하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class DailyRecordQueryRepository {

    /** summary는 승인 항목의 content를 item_no 순서로 " · "로 이은 한 줄 */
    private static final String SUMMARY_SEPARATOR = " · ";

    private final JdbcClient jdbc;

    public record DailyRecordRow(Long diaryId, LocalDate diaryDate, int approvedCount, String summary) {
    }

    public record ApprovedItemRow(LocalDate diaryDate, String category, String content, String timeSlot,
                                  Integer durationMin) {
    }

    /** 승인 항목이 하나 이상 있는 일기. 최신 날짜가 먼저 */
    public List<DailyRecordRow> findDailyRecords(Long subjectId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT d.diary_id, d.diary_date, count(*) AS approved_count,
                               string_agg(i.content, :sep ORDER BY i.item_no) AS summary
                        FROM diary d
                        JOIN extracted_item i ON i.diary_id = d.diary_id AND i.status = 'APPROVED'
                        WHERE d.subject_id = :subjectId AND d.diary_date BETWEEN :from AND :to
                        GROUP BY d.diary_id, d.diary_date
                        ORDER BY d.diary_date DESC, d.diary_id DESC
                        """)
            .param("sep", SUMMARY_SEPARATOR)
            .param("subjectId", subjectId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new DailyRecordRow(
                rs.getLong("diary_id"),
                rs.getObject("diary_date", LocalDate.class),
                rs.getInt("approved_count"),
                rs.getString("summary")))
            .list();
    }

    /** 이 대상자의 일기에 속한 승인 항목. 일기가 없거나 다른 대상자의 것이거나 승인 항목이 없으면 빈 목록 */
    public List<ApprovedItemRow> findApprovedItems(Long subjectId, Long diaryId) {
        return jdbc.sql("""
                        SELECT d.diary_date, i.category, i.content, i.time_slot, i.duration_min
                        FROM diary d
                        JOIN extracted_item i ON i.diary_id = d.diary_id AND i.status = 'APPROVED'
                        WHERE d.diary_id = :diaryId AND d.subject_id = :subjectId
                        ORDER BY i.item_no
                        """)
            .param("diaryId", diaryId)
            .param("subjectId", subjectId)
            .query((rs, rowNum) -> new ApprovedItemRow(
                rs.getObject("diary_date", LocalDate.class),
                rs.getString("category"),
                rs.getString("content"),
                rs.getString("time_slot"),
                rs.getObject("duration_min", Integer.class)))
            .list();
    }
}
