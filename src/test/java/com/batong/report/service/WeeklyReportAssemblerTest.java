package com.batong.report.service;

import com.batong.global.enums.GuideSection;
import com.batong.global.enums.TimeSlot;
import com.batong.report.dto.ReportDtos.WeeklyReportResponse;
import com.batong.report.repository.WeeklyReportQueryRepository.BehaviorCountRow;
import com.batong.report.repository.WeeklyReportQueryRepository.DailyCountRow;
import com.batong.report.repository.WeeklyReportQueryRepository.KeyChangeRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** REPORT-03 집계 규칙: 7일 추이, 일기 수와 행동 횟수 구분, 정렬, 0건 처리 */
class WeeklyReportAssemblerTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 28);
    private static final LocalDate END = LocalDate.of(2026, 11, 3);

    @Test
    void 기록이_있으면_dailyTrend는_기록_없는_날도_포함해_7일_모두_주고_일기_수와_행동_횟수를_따로_센다() {
        WeeklyReportResponse r = WeeklyReportAssembler.assemble(START, END,
            List.of(new DailyCountRow(LocalDate.of(2026, 10, 28), 1, 2),
                new DailyCountRow(LocalDate.of(2026, 10, 31), 2, 0),
                new DailyCountRow(LocalDate.of(2026, 11, 3), 2, 5)),
            List.of(), List.of());

        assertThat(r.recordCount()).isEqualTo(5);
        assertThat(r.behaviorCount()).isEqualTo(7);
        assertThat(r.dailyTrend()).hasSize(7);
        assertThat(r.dailyTrend().get(0).date()).isEqualTo(START);
        assertThat(r.dailyTrend().get(0).recordCount()).isEqualTo(1);
        assertThat(r.dailyTrend().get(1).date()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(r.dailyTrend().get(1).recordCount()).isZero();
        assertThat(r.dailyTrend().get(1).behaviorCount()).isZero();
        assertThat(r.dailyTrend().get(6).date()).isEqualTo(END);
        assertThat(r.summaryLines()).isEmpty();
    }

    @Test
    void 기록이_0건이면_건수_0과_빈_배열만_준다() {
        WeeklyReportResponse r = WeeklyReportAssembler.assemble(START, END, List.of(), List.of(),
            List.of(new KeyChangeRow(1L, END, "PREFERENCE", "무시되어야 함")));

        assertThat(r.recordCount()).isZero();
        assertThat(r.behaviorCount()).isZero();
        assertThat(r.summaryLines()).isEmpty();
        assertThat(r.dailyTrend()).isEmpty();
        assertThat(r.keyChanges()).isEmpty();
        assertThat(r.repeatedBehaviors()).isEmpty();
        assertThat(r.timePattern()).isEmpty();
    }

    @Test
    void repeatedBehaviors는_label별_합계를_많은_순으로_주고_label이_없는_항목은_뺀다() {
        WeeklyReportResponse r = WeeklyReportAssembler.assemble(START, END,
            List.of(new DailyCountRow(END, 3, 6)),
            List.of(new BehaviorCountRow("소리 지름", "AFTERNOON", 2),
                new BehaviorCountRow("소리 지름", "EVENING", 1),
                new BehaviorCountRow("물건 파손", "AFTERNOON", 1),
                new BehaviorCountRow("반복 행동", null, 1),
                new BehaviorCountRow(null, "NIGHT", 1)),
            List.of());

        assertThat(r.repeatedBehaviors()).extracting("label", "count")
            .containsExactly(
                tuple("소리 지름", 3),
                tuple("물건 파손", 1),
                tuple("반복 행동", 1));
    }

    @Test
    void timePattern은_시간대별_합계를_많은_순으로_같으면_아침부터_밤_순으로_주고_시간대가_없는_항목은_뺀다() {
        WeeklyReportResponse r = WeeklyReportAssembler.assemble(START, END,
            List.of(new DailyCountRow(END, 3, 6)),
            List.of(new BehaviorCountRow("소리 지름", "AFTERNOON", 2),
                new BehaviorCountRow("물건 파손", "AFTERNOON", 1),
                new BehaviorCountRow("소리 지름", "NIGHT", 1),
                new BehaviorCountRow("반복 행동", "MORNING", 1),
                new BehaviorCountRow("반복 행동", null, 4)),
            List.of());

        assertThat(r.timePattern()).extracting("timeSlot", "count")
            .containsExactly(
                tuple(TimeSlot.AFTERNOON, 3),
                tuple(TimeSlot.MORNING, 1),
                tuple(TimeSlot.NIGHT, 1));
    }

    @Test
    void keyChanges는_조회한_순서와_값을_그대로_옮긴다() {
        WeeklyReportResponse r = WeeklyReportAssembler.assemble(START, END,
            List.of(new DailyCountRow(END, 1, 0)), List.of(),
            List.of(new KeyChangeRow(503L, LocalDate.of(2026, 10, 31), "PREFERENCE", "담요를 덮어주면 안정됨")));

        assertThat(r.keyChanges()).hasSize(1);
        assertThat(r.keyChanges().get(0).diaryId()).isEqualTo(503L);
        assertThat(r.keyChanges().get(0).diaryDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(r.keyChanges().get(0).section()).isEqualTo(GuideSection.PREFERENCE);
        assertThat(r.keyChanges().get(0).content()).isEqualTo("담요를 덮어주면 안정됨");
    }
}
