package com.batong.diary.service;

import com.batong.diary.domain.Diary;
import com.batong.diary.domain.ReviewStatus;
import com.batong.diary.dto.DiaryDtos.DetailItem;
import com.batong.diary.dto.DiaryDtos.DiaryCreateRequest;
import com.batong.diary.dto.DiaryDtos.DiaryCreateResponse;
import com.batong.diary.dto.DiaryDtos.DiaryDetailResponse;
import com.batong.diary.dto.DiaryDtos.DiaryListResponse;
import com.batong.diary.dto.DiaryDtos.DiarySummary;
import com.batong.diary.repository.DiaryRepository;
import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.enums.AnalysisStatus;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.ItemStatus;
import com.batong.global.enums.SafetyLevel;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** DIARY-01·03~05. DIARY-02는 {@link com.batong.analysis.service.DiaryAnalysisService}. 일기 원문을 다루므로 모두 OWNER만(TRD 7-2) */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_LENGTH = 30;

    private final DiaryRepository diaryRepository;
    private final SubjectRoleResolver roleResolver;
    private final JdbcClient jdbc;

    @Transactional
    public DiaryCreateResponse create(Long subjectId, Long userId, DiaryCreateRequest request) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        Diary diary = diaryRepository.save(
                Diary.create(subjectId, request.diaryDate(), request.content(), request.inputType()));
        return new DiaryCreateResponse(diary.getId(), diary.getAnalysisStatus());
    }

    /** diary_date 최신순, 같은 날은 나중에 쓴 것 먼저. from·to는 포함 범위이고 둘 다 선택 */
    @Transactional(readOnly = true)
    public DiaryListResponse list(Long subjectId, Long userId, LocalDate from, LocalDate to, int page, int size) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (from != null && to != null && from.isAfter(to))) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        StringBuilder where = new StringBuilder("d.subject_id = :s");
        Map<String, Object> params = new HashMap<>();
        params.put("s", subjectId);
        if (from != null) {
            where.append(" AND d.diary_date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            where.append(" AND d.diary_date <= :to");
            params.put("to", to);
        }

        long total = jdbc.sql("SELECT count(*) FROM diary d WHERE " + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<DiarySummary> content = jdbc.sql("""
                SELECT d.diary_id, d.diary_date, d.content, d.analysis_status,
                       count(i.item_no) FILTER (WHERE i.status = 'PENDING') AS pending,
                       count(i.item_no) FILTER (WHERE i.status = 'APPROVED') AS approved,
                       EXISTS (SELECT 1 FROM ai_analysis a WHERE a.diary_id = d.diary_id) AS analyzed
                FROM diary d LEFT JOIN extracted_item i ON i.diary_id = d.diary_id
                WHERE %s
                GROUP BY d.diary_id
                ORDER BY d.diary_date DESC, d.diary_id DESC
                LIMIT :limit OFFSET :offset""".formatted(where))
                .params(params)
                .query((rs, n) -> {
                    int pending = rs.getInt("pending");
                    return new DiarySummary(rs.getLong("diary_id"), rs.getDate("diary_date").toLocalDate(),
                            preview(rs.getString("content")), AnalysisStatus.valueOf(rs.getString("analysis_status")),
                            reviewStatus(rs.getBoolean("analyzed"), pending), pending, rs.getInt("approved"));
                })
                .list();
        return new DiaryListResponse(content, page, size, total);
    }

    /** 공감 문장은 가장 최근 분석의 것. 최근 분석이 위기면 empathyText null, items 비움(위기 여부 자체는 내보내지 않음) */
    @Transactional(readOnly = true)
    public DiaryDetailResponse get(Long subjectId, Long diaryId, Long userId) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        Diary diary = diaryRepository.findByIdAndSubjectId(diaryId, subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));

        record Latest(SafetyLevel safetyLevel, String empathyText) { }
        Latest latest = jdbc.sql("""
                SELECT safety_level, empathy_text FROM ai_analysis
                WHERE diary_id = :d ORDER BY analysis_no DESC LIMIT 1""")
                .param("d", diaryId)
                .query((rs, n) -> new Latest(SafetyLevel.valueOf(rs.getString("safety_level")), rs.getString("empathy_text")))
                .optional().orElse(null);

        List<DetailItem> items = jdbc.sql("""
                SELECT item_no, category, content, evidence_quote, status, edited
                FROM extracted_item WHERE diary_id = :d ORDER BY item_no""")
                .param("d", diaryId)
                .query((rs, n) -> {
                    ItemCategory category = ItemCategory.valueOf(rs.getString("category"));
                    return new DetailItem(rs.getInt("item_no"), category, category.getSection(), rs.getString("content"),
                            rs.getString("evidence_quote"), ItemStatus.valueOf(rs.getString("status")),
                            rs.getBoolean("edited"));
                })
                .list();
        int pending = (int) items.stream().filter(i -> i.status() == ItemStatus.PENDING).count();
        ReviewStatus reviewStatus = reviewStatus(latest != null, pending);
        boolean crisis = latest != null && latest.safetyLevel() == SafetyLevel.CRISIS;

        return new DiaryDetailResponse(diary.getId(), diary.getDiaryDate(), diary.getContent(), diary.getInputType(),
                diary.getAnalysisStatus(), reviewStatus, latest == null ? null : latest.empathyText(),
                crisis ? List.of() : items);
    }

    /** 분석 중이면 저장 단계가 일기를 잃지 않도록 409 */
    @Transactional
    public void delete(Long subjectId, Long diaryId, Long userId) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        int deleted = diaryRepository.deleteUnlessAnalyzing(diaryId, subjectId, AnalysisStatus.PROCESSING);
        if (deleted == 0) {
            boolean exists = diaryRepository.findByIdAndSubjectId(diaryId, subjectId).isPresent();
            throw new BusinessException(exists ? ErrorCode.DIARY_ALREADY_ANALYZING : ErrorCode.COMMON_NOT_FOUND);
        }
    }

    /** API 명세 DIARY-03: 분석 전 NONE, PENDING 항목이 있으면 PENDING, 없으면 APPROVED */
    static ReviewStatus reviewStatus(boolean analyzed, int pendingCount) {
        if (!analyzed) {
            return ReviewStatus.NONE;
        }
        return pendingCount > 0 ? ReviewStatus.PENDING : ReviewStatus.APPROVED;
    }

    /** HOME-01과 같은 규칙: 공백을 하나로 줄이고 30자가 넘으면 자른 뒤 "..." */
    static String preview(String content) {
        String flat = content.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= PREVIEW_LENGTH) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_LENGTH)) + "...";
    }
}
