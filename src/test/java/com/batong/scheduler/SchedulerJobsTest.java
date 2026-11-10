package com.batong.scheduler;

import com.batong.scheduler.service.SchedulerJobs;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** TRD 12-6 스케줄러 시각이 명세와 같은지 확인한다. DB 없이 돈다. */
class SchedulerJobsTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void 공유_초안_정리는_매일_03시_00분이다() throws Exception {
        CronExpression cron = cronOf("purgeStaleDrafts");

        assertThat(cron.next(kst(2026, 11, 10, 2, 59, 0))).isEqualTo(kst(2026, 11, 10, 3, 0, 0));
        assertThat(cron.next(kst(2026, 11, 10, 3, 0, 0))).isEqualTo(kst(2026, 11, 11, 3, 0, 0));
    }

    @Test
    void 고정본_파기는_매일_03시_10분이다() throws Exception {
        CronExpression cron = cronOf("clearExpiredSnapshots");

        assertThat(cron.next(kst(2026, 11, 10, 3, 0, 0))).isEqualTo(kst(2026, 11, 10, 3, 10, 0));
        assertThat(cron.next(kst(2026, 11, 10, 3, 10, 0))).isEqualTo(kst(2026, 11, 11, 3, 10, 0));
    }

    @Test
    void 분석_상태_복구는_5분마다다() throws Exception {
        CronExpression cron = cronOf("recoverStuckAnalyses");

        assertThat(cron.next(kst(2026, 11, 10, 14, 3, 20))).isEqualTo(kst(2026, 11, 10, 14, 5, 0));
        assertThat(cron.next(kst(2026, 11, 10, 14, 5, 0))).isEqualTo(kst(2026, 11, 10, 14, 10, 0));
        assertThat(cron.next(kst(2026, 11, 10, 14, 55, 0))).isEqualTo(kst(2026, 11, 10, 15, 0, 0));
    }

    @Test
    void 모든_작업이_한국_시간_기준이다() throws Exception {
        for (String method : new String[]{"purgeStaleDrafts", "clearExpiredSnapshots", "recoverStuckAnalyses"}) {
            assertThat(scheduled(method).zone()).as(method).isEqualTo("Asia/Seoul");
        }
    }

    private static Scheduled scheduled(String method) throws NoSuchMethodException {
        return SchedulerJobs.class.getMethod(method).getAnnotation(Scheduled.class);
    }

    private static CronExpression cronOf(String method) throws NoSuchMethodException {
        return CronExpression.parse(scheduled(method).cron());
    }

    private static ZonedDateTime kst(int y, int mo, int d, int h, int mi, int s) {
        return ZonedDateTime.of(y, mo, d, h, mi, s, 0, KST);
    }
}
