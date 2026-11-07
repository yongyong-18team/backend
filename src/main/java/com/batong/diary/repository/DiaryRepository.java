package com.batong.diary.repository;

import com.batong.diary.domain.Diary;
import com.batong.global.enums.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByIdAndSubjectId(Long id, Long subjectId);

    /**
     * 분석 중이 아닐 때만 PROCESSING으로 바꾼다(TRD 8-3 Tx1 조건부 UPDATE).
     * 조회 후 변경이 아니라 한 문장이라 동시에 두 번 눌러도 하나만 1을 받는다.
     *
     * @return 바뀐 행 수. 0이면 없는 일기이거나 이미 분석 중
     */
    @Modifying(clearAutomatically = true)
    @Query("update Diary d set d.analysisStatus = :processing, d.updatedAt = :now "
            + "where d.id = :diaryId and d.subjectId = :subjectId and d.analysisStatus <> :processing")
    int startAnalysis(@Param("diaryId") Long diaryId, @Param("subjectId") Long subjectId,
                      @Param("processing") AnalysisStatus processing, @Param("now") Instant now);
}
