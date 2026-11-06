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
}
