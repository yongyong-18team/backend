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
import com.batong.global.enums.InputType;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.ItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * DIARY-01 구현. DIARY-02는 {@link com.batong.analysis.service.DiaryAnalysisService}.
 * DIARY-03~05는 스텁: API 명세 예시를 돌려준다. 실제 구현은 #27
 */
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
}
