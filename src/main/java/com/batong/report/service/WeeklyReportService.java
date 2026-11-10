package com.batong.report.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.config.JacksonConfig;
import com.batong.report.dto.ReportDtos.WeeklyReportResponse;
import com.batong.report.repository.WeeklyReportQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * REPORT-03 주간 리포트. 승인(APPROVED) 항목만 집계하고 일기 원문·공감 문장·근거 문장은 읽지도 내보내지도 않는다
 * (TRD 7-2 공동 관리자 조회 범위). OWNER와 CO 모두 같은 응답을 받는다.
 * LLM 호출(AI-05)은 DB 트랜잭션 밖에서 한다(TRD 8-3).
 */
@Service
public class WeeklyReportService {

    /** endDate 포함 최근 7일 (API 명세 REPORT-03) */
    static final int PERIOD_DAYS = 7;

    private final WeeklyReportQueryRepository queries;
    private final WeeklySummaryGenerator summaryGenerator;
    private final SubjectRoleResolver roleResolver;
    private final TransactionTemplate readOnlyTx;

    public WeeklyReportService(WeeklyReportQueryRepository queries,
                               WeeklySummaryGenerator summaryGenerator,
                               SubjectRoleResolver roleResolver,
                               PlatformTransactionManager transactionManager) {
        this.queries = queries;
        this.summaryGenerator = summaryGenerator;
        this.roleResolver = roleResolver;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    /** endDate를 생략하면 오늘(KST). 기간은 endDate-6 ~ endDate */
    public WeeklyReportResponse getWeeklyReport(Long subjectId, Long userId, LocalDate endDate) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);

        LocalDate end = endDate != null ? endDate : LocalDate.now(JacksonConfig.KST);
        LocalDate start = end.minusDays(PERIOD_DAYS - 1L);

        // 집계 조회는 한 트랜잭션에서 같은 시점으로 읽고, LLM은 그 뒤에 부른다
        Aggregated aggregated = readOnlyTx.execute(status -> new Aggregated(
            WeeklyReportAssembler.assemble(start, end,
                queries.findDailyCounts(subjectId, start, end),
                queries.findBehaviorCounts(subjectId, start, end),
                queries.findKeyChanges(subjectId, start, end)),
            queries.findSubjectName(subjectId)));

        WeeklyReportResponse stats = aggregated.report();
        List<String> summaryLines = summaryGenerator.summarize(stats, aggregated.subjectName());
        return new WeeklyReportResponse(stats.periodStart(), stats.periodEnd(),
            stats.recordCount(), stats.behaviorCount(), summaryLines,
            stats.dailyTrend(), stats.keyChanges(), stats.repeatedBehaviors(), stats.timePattern());
    }

    private record Aggregated(WeeklyReportResponse report, String subjectName) {
    }
}
