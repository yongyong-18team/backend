package com.batong.report.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.config.JacksonConfig;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.TimeSlot;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.report.dto.ReportDtos.DailyRecordListResponse;
import com.batong.report.dto.ReportDtos.DailyRecordResponse;
import com.batong.report.dto.ReportDtos.DailyRecordSummary;
import com.batong.report.dto.ReportDtos.RecordItem;
import com.batong.report.dto.ReportDtos.SectionItems;
import com.batong.report.repository.DailyRecordQueryRepository;
import com.batong.report.repository.DailyRecordQueryRepository.ApprovedItemRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * REPORT-01·02 일일 기록. 승인(APPROVED) 항목만 다룬다. 일기 원문·공감 문장·근거 문장은 읽지도 내보내지도 않는다
 * (API 명세 REPORT-02, TRD 7-2 공동 관리자 조회 범위). OWNER와 CO 모두 같은 응답을 받는다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** from·to를 생략했을 때 기본 구간 길이(일). 명세 REPORT-01 예시(10/28~11/3)와 REPORT-03(최근 7일)에 맞춘다 */
    static final int DEFAULT_PERIOD_DAYS = 7;

    private final DailyRecordQueryRepository queries;
    private final SubjectRoleResolver roleResolver;

    /**
     * 기본 구간: to 생략 시 오늘(KST), from 생략 시 to 포함 최근 7일. 응답의 from·to는 실제 적용한 구간이다.
     * from이 to보다 늦으면 COMMON_VALIDATION
     */
    @Transactional(readOnly = true)
    public DailyRecordListResponse getDailyRecords(Long subjectId, Long userId, LocalDate from, LocalDate to) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);

        LocalDate end = to != null ? to : LocalDate.now(JacksonConfig.KST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }

        List<DailyRecordSummary> records = queries.findDailyRecords(subjectId, start, end).stream()
            .map(r -> new DailyRecordSummary(r.diaryId(), r.diaryDate(), r.approvedCount(), r.summary()))
            .toList();
        return new DailyRecordListResponse(start, end, records);
    }

    /**
     * 섹션은 저장하지 않고 category로 계산하며(ItemCategory), 섹션 순서는 GuideSection 선언 순서(PRD 3.1),
     * 섹션 안의 항목은 item_no 순서다. 승인 항목이 있는 섹션만 내려준다.
     * 없는 일기, 다른 대상자의 일기, 승인 항목이 없는 일기는 모두 COMMON_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public DailyRecordResponse getDailyRecord(Long subjectId, Long userId, Long diaryId) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);

        List<ApprovedItemRow> rows = queries.findApprovedItems(subjectId, diaryId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }

        Map<GuideSection, List<RecordItem>> bySection = new EnumMap<>(GuideSection.class);
        for (ApprovedItemRow row : rows) {
            GuideSection section = ItemCategory.valueOf(row.category()).getSection();
            TimeSlot timeSlot = row.timeSlot() == null ? null : TimeSlot.valueOf(row.timeSlot());
            bySection.computeIfAbsent(section, s -> new ArrayList<>())
                .add(new RecordItem(row.content(), timeSlot, row.durationMin()));
        }

        List<SectionItems> sections = bySection.entrySet().stream()
            .map(e -> new SectionItems(e.getKey(), e.getValue()))
            .toList();
        return new DailyRecordResponse(diaryId, rows.get(0).diaryDate(), sections);
    }
}
