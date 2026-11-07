package com.batong.diary.dto;

import com.batong.diary.domain.AnalysisResult;
import com.batong.diary.domain.ReviewStatus;
import com.batong.global.enums.AnalysisStatus;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.InputType;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.ItemStatus;
import com.batong.global.enums.TimeSlot;

import java.time.LocalDate;
import java.util.List;

/** STT-01, DIARY-01~05 요청·응답 */
public final class DiaryDtos {

    private DiaryDtos() {
    }

    public record SttResponse(String text) {
    }

    public record DiaryCreateRequest(LocalDate diaryDate, String content, InputType inputType) {
    }

    public record DiaryCreateResponse(Long diaryId, AnalysisStatus analysisStatus) {
    }

    /** CRISIS면 empathyText null, items 비어 있음 */
    public record AnalyzeResponse(int analysisNo, AnalysisResult result, String empathyText, int itemCount,
                                  List<AnalyzedItem> items, boolean burnoutNotice) {
    }

    /** section은 서버가 category를 매핑한 값. label은 BEHAVIOR만 */
    public record AnalyzedItem(int itemNo, ItemCategory category, GuideSection section, String label, String content,
                               TimeSlot timeSlot, Integer durationMin, Integer episodeNo, String evidenceQuote,
                               ItemStatus status) {
    }

    public record DiaryListResponse(List<DiarySummary> content, int page, int size, long totalElements) {
    }

    public record DiarySummary(Long diaryId, LocalDate diaryDate, String preview, AnalysisStatus analysisStatus,
                               ReviewStatus reviewStatus, int pendingCount, int approvedCount) {
    }

    /** 위기 판정 일기는 empathyText null, items 비어 있음. 위기 여부 자체는 넣지 않는다 */
    public record DiaryDetailResponse(Long diaryId, LocalDate diaryDate, String content, InputType inputType,
                                      AnalysisStatus analysisStatus, ReviewStatus reviewStatus, String empathyText,
                                      List<DetailItem> items) {
    }

    public record DetailItem(int itemNo, ItemCategory category, GuideSection section, String content,
                             String evidenceQuote, ItemStatus status, boolean edited) {
    }
}
