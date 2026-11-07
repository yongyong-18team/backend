package com.batong.diary.service;

import com.batong.diary.domain.AnalysisResult;
import com.batong.diary.domain.ReviewStatus;
import com.batong.diary.dto.DiaryDtos.AnalyzeResponse;
import com.batong.diary.dto.DiaryDtos.AnalyzedItem;
import com.batong.diary.dto.DiaryDtos.DetailItem;
import com.batong.diary.dto.DiaryDtos.DiaryCreateRequest;
import com.batong.diary.dto.DiaryDtos.DiaryCreateResponse;
import com.batong.diary.dto.DiaryDtos.DiaryDetailResponse;
import com.batong.diary.dto.DiaryDtos.DiaryListResponse;
import com.batong.diary.dto.DiaryDtos.DiarySummary;
import com.batong.global.enums.AnalysisStatus;
import com.batong.global.enums.BehaviorLabel;
import com.batong.global.enums.InputType;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.ItemStatus;
import com.batong.global.enums.TimeSlot;
import com.batong.diary.domain.Diary;
import com.batong.diary.repository.DiaryRepository;
import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** DIARY-01 구현. DIARY-02~05는 스텁: API 명세 예시를 돌려준다. 실제 구현은 #26(DIARY-02), #27(DIARY-03~05) */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final LocalDate DIARY_DATE = LocalDate.of(2026, 11, 3);

    private final DiaryRepository diaryRepository;
    private final SubjectRoleResolver roleResolver;

    /** 일기 원문을 다루므로 OWNER만(TRD 7-2) */
    @Transactional
    public DiaryCreateResponse create(Long subjectId, Long userId, DiaryCreateRequest request) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        Diary diary = diaryRepository.save(
                Diary.create(subjectId, request.diaryDate(), request.content(), request.inputType()));
        return new DiaryCreateResponse(diary.getId(), diary.getAnalysisStatus());
    }

    /** 명세 예시는 itemCount 3에 항목 2개만 적혀 있어, AI 명세 3-3 예시의 세 번째(RESPONSE)까지 넣었다 */
    public AnalyzeResponse analyze(Long subjectId, Long diaryId, Long userId) {
        List<AnalyzedItem> items = List.of(
                item(1, ItemCategory.TRIGGER, null, "옆 건물 공사 소리가 나자 불안해함", null,
                        "오후에 옆 건물 공사 소리가 나니까"),
                item(2, ItemCategory.BEHAVIOR, BehaviorLabel.SCREAMING.getText(), "30분 넘게 소리를 지름", 30,
                        "30분 넘게 소리를 질렀다"),
                item(3, ItemCategory.RESPONSE, null, "차에 태워 트로트를 틀어주니 조용해짐", null,
                        "차에 태워서 트로트 틀어주니까 조용해졌다"));
        return new AnalyzeResponse(1, AnalysisResult.REVIEW, "공사 소리 때문에 오후 내내 긴장하셨겠어요.",
                items.size(), items, false);
    }

    public DiaryListResponse list(Long subjectId, Long userId, LocalDate from, LocalDate to, int page, int size) {
        return new DiaryListResponse(List.of(new DiarySummary(501L, DIARY_DATE, "오후에 옆 건물...",
                AnalysisStatus.DONE, ReviewStatus.APPROVED, 0, 3)), page, size, 14);
    }

    public DiaryDetailResponse get(Long subjectId, Long diaryId, Long userId) {
        return new DiaryDetailResponse(diaryId, DIARY_DATE,
                "오후에 옆 건물 공사 소리가 나니까 민지가 귀를 막더니 30분 넘게 소리를 질렀다. 차에 태워서 트로트 틀어주니까 조용해졌다.",
                InputType.VOICE, AnalysisStatus.DONE, ReviewStatus.APPROVED, "공사 소리 때문에 오후 내내 긴장하셨겠어요.",
                List.of(new DetailItem(1, ItemCategory.TRIGGER, ItemCategory.TRIGGER.getSection(),
                        "옆 건물 공사 소리가 나자 불안해함", "오후에 옆 건물 공사 소리가 나니까", ItemStatus.APPROVED, false)));
    }

    public void delete(Long subjectId, Long diaryId, Long userId) {
    }

    private static AnalyzedItem item(int itemNo, ItemCategory category, String label, String content,
                                     Integer durationMin, String evidenceQuote) {
        return new AnalyzedItem(itemNo, category, category.getSection(), label, content, TimeSlot.AFTERNOON,
                durationMin, 1, evidenceQuote, ItemStatus.PENDING);
    }
}
