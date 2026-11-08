package com.batong.diary.domain;

import com.batong.global.enums.AnalysisStatus;
import com.batong.global.enums.InputType;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 일기 (ERD 3-2 diary). 작성자는 대상자의 주 보호자로 유도한다. 음성은 저장하지 않는다 */
@Getter
@Entity
@Table(name = "diary")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "diary_date", nullable = false)
    private LocalDate diaryDate;

    /** STT 결과를 보호자가 확인·수정한 최종본. 로그에 남기지 않는다 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 10)
    private InputType inputType;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 12)
    private AnalysisStatus analysisStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** DIARY-01. 본문 앞뒤 공백은 잘라 저장하고, 분석 전 상태(PENDING)로 시작한다 */
    public static Diary create(Long subjectId, LocalDate diaryDate, String content, InputType inputType) {
        if (diaryDate == null || inputType == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        Diary diary = new Diary();
        diary.subjectId = subjectId;
        diary.diaryDate = diaryDate;
        diary.content = validContent(content);
        diary.inputType = inputType;
        diary.analysisStatus = AnalysisStatus.PENDING;
        diary.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        diary.updatedAt = diary.createdAt;
        return diary;
    }

    private static String validContent(String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.DIARY_EMPTY_CONTENT);
        }
        if (!storable(content)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        return content;
    }

    /** PostgreSQL은 NUL을 저장하지 못하고, 짝 없는 서로게이트는 저장할 때 조용히 '?'로 바뀐다(#65와 같은 규칙) */
    private static boolean storable(String s) {
        return s.indexOf('\u0000') < 0 && s.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF);
    }
}
