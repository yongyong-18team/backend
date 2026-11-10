package com.batong.report.service;

import com.batong.global.enums.BehaviorLabel;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.TimeSlot;
import com.batong.report.dto.ReportDtos.DailyTrend;
import com.batong.report.dto.ReportDtos.KeyChange;
import com.batong.report.dto.ReportDtos.RepeatedBehavior;
import com.batong.report.dto.ReportDtos.TimePatternCount;
import com.batong.report.dto.ReportDtos.WeeklyReportResponse;
import com.batong.report.repository.WeeklyReportQueryRepository.BehaviorCountRow;
import com.batong.report.repository.WeeklyReportQueryRepository.DailyCountRow;
import com.batong.report.repository.WeeklyReportQueryRepository.KeyChangeRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REPORT-03 집계 행을 응답으로 조립한다. DB도 LLM도 쓰지 않는 순수 계산이라 단위 테스트로 규칙을 검증한다.
 * summaryLines는 비워 두고, 채우는 일은 WeeklySummaryGenerator(AI-05)가 한다. AI가 판단하는 값은 없다.
 */
final class WeeklyReportAssembler {

    private WeeklyReportAssembler() {
    }

    /**
     * 기록(승인 항목이 있는 일기)이 0건이면 건수 0과 빈 배열만 준다(API 명세 REPORT-03, 화면은 "생성 불가" 안내).
     * 기록이 있으면 dailyTrend는 기록 없는 날도 포함해 7일 모두 내려준다(그래프용).
     */
    static WeeklyReportResponse assemble(LocalDate periodStart, LocalDate periodEnd,
                                         List<DailyCountRow> daily,
                                         List<BehaviorCountRow> behaviors,
                                         List<KeyChangeRow> keyChanges) {
        Map<LocalDate, DailyCountRow> byDate = new HashMap<>();
        daily.forEach(row -> byDate.put(row.date(), row));

        List<DailyTrend> trend = new ArrayList<>();
        int recordCount = 0;
        int behaviorCount = 0;
        for (LocalDate d = periodStart; !d.isAfter(periodEnd); d = d.plusDays(1)) {
            DailyCountRow row = byDate.get(d);
            int records = row == null ? 0 : row.recordCount();
            int behaviorsOfDay = row == null ? 0 : row.behaviorCount();
            trend.add(new DailyTrend(d, records, behaviorsOfDay));
            recordCount += records;
            behaviorCount += behaviorsOfDay;
        }

        if (recordCount == 0) {
            return new WeeklyReportResponse(periodStart, periodEnd, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
        }

        return new WeeklyReportResponse(periodStart, periodEnd, recordCount, behaviorCount,
            List.of(), trend, toKeyChanges(keyChanges), repeatedBehaviors(behaviors), timePattern(behaviors));
    }

    /** 승인된 BEHAVIOR 항목을 label별로 센다. 많은 순, 같으면 label 순. label이 없는 항목은 건너뛴다 */
    private static List<RepeatedBehavior> repeatedBehaviors(List<BehaviorCountRow> behaviors) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BehaviorCountRow row : behaviors) {
            if (row.label() != null) {
                counts.merge(row.label(), row.count(), Integer::sum);
            }
        }
        return counts.entrySet().stream()
            .map(e -> new RepeatedBehavior(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(RepeatedBehavior::count).reversed()
                .thenComparing(r -> labelOrder(r.label())))
            .toList();
    }

    /** 승인된 BEHAVIOR 항목을 시간대별로 센다. 많은 순, 같으면 아침→밤 순. 시간대가 없는 항목은 건너뛴다 */
    private static List<TimePatternCount> timePattern(List<BehaviorCountRow> behaviors) {
        Map<TimeSlot, Integer> counts = new EnumMap<>(TimeSlot.class);
        for (BehaviorCountRow row : behaviors) {
            if (row.timeSlot() != null) {
                counts.merge(TimeSlot.valueOf(row.timeSlot()), row.count(), Integer::sum);
            }
        }
        return counts.entrySet().stream()
            .map(e -> new TimePatternCount(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(TimePatternCount::count).reversed()
                .thenComparing(t -> t.timeSlot().ordinal()))
            .toList();
    }

    private static List<KeyChange> toKeyChanges(List<KeyChangeRow> rows) {
        return rows.stream()
            .map(r -> new KeyChange(r.diaryId(), r.diaryDate(), GuideSection.valueOf(r.section()), r.content()))
            .toList();
    }

    /** 같은 횟수일 때 BehaviorLabel 선언 순서(ERD 목록 순서). 목록에 없는 값은 뒤로 */
    private static int labelOrder(String label) {
        return BehaviorLabel.fromText(label).map(Enum::ordinal).orElse(Integer.MAX_VALUE);
    }
}
