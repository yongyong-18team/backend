package com.batong.report.service;

import com.batong.global.enums.GuideSection;
import com.batong.global.enums.TimeSlot;
import com.batong.report.dto.ReportDtos.DailyRecordListResponse;
import com.batong.report.dto.ReportDtos.DailyRecordResponse;
import com.batong.report.dto.ReportDtos.DailyRecordSummary;
import com.batong.report.dto.ReportDtos.RecordItem;
import com.batong.report.dto.ReportDtos.SectionItems;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    // 스텁: 고정 응답. 실제 구현에서 승인 항목이 있는 일기 조회로 교체
    public DailyRecordListResponse getDailyRecords(Long subjectId, LocalDate from, LocalDate to) {
        return new DailyRecordListResponse(from, to,
            List.of(new DailyRecordSummary(501L, LocalDate.of(2026, 11, 3), 3, "큰 소리에 불안 반응 · 음악으로 안정")));
    }

    // 스텁: API 명세 REPORT-02 예시. 승인(APPROVED) 항목만, 원문·공감 문장·근거 문장은 넣지 않는다
    public DailyRecordResponse getDailyRecord(Long subjectId, Long diaryId) {
        return new DailyRecordResponse(501L, LocalDate.of(2026, 11, 3),
            List.of(new SectionItems(GuideSection.BEHAVIOR_RESPONSE,
                List.of(new RecordItem("30분 넘게 소리를 지름", TimeSlot.AFTERNOON, 30)))));
    }
}
