package com.batong.report.dto;

import com.batong.global.enums.GuideSection;
import com.batong.global.enums.TimeSlot;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    // REPORT-01 (명세에 응답 예시 없음)
    public record DailyRecordListResponse(LocalDate from, LocalDate to, List<DailyRecordSummary> records) {
    }

    public record DailyRecordSummary(Long diaryId, LocalDate diaryDate, int approvedCount, String summary) {
    }

    // REPORT-02 (명세 예시)
    public record DailyRecordResponse(Long diaryId, LocalDate diaryDate, List<SectionItems> sections) {
    }

    public record SectionItems(GuideSection section, List<RecordItem> items) {
    }

    public record RecordItem(String content, TimeSlot timeSlot, Integer durationMin) {
    }

    // REPORT-03 (명세 예시). 일기 원문·근거 문장·공감 문장은 없다
    public record WeeklyReportResponse(LocalDate periodStart, LocalDate periodEnd,
                                       int recordCount, int behaviorCount,
                                       List<String> summaryLines,
                                       List<DailyTrend> dailyTrend,
                                       List<KeyChange> keyChanges,
                                       List<RepeatedBehavior> repeatedBehaviors,
                                       List<TimePatternCount> timePattern) {
    }

    public record DailyTrend(LocalDate date, int recordCount, int behaviorCount) {
    }

    public record KeyChange(Long diaryId, LocalDate diaryDate, GuideSection section, String content) {
    }

    public record RepeatedBehavior(String label, int count) {
    }

    public record TimePatternCount(TimeSlot timeSlot, int count) {
    }
}
