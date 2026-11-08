package com.batong.analysis.repository;

import com.batong.analysis.domain.ExtractedItem;
import com.batong.global.enums.ItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExtractedItemRepository extends JpaRepository<ExtractedItem, ExtractedItem.Key> {

    Optional<ExtractedItem> findTopByDiaryIdOrderByItemNoDesc(Long diaryId);

    /** 재분석 시 검토 대기 항목만 지운다. 승인·제외 항목은 남긴다(TRD 8-5) */
    @Modifying
    @Query("delete from ExtractedItem e where e.diaryId = :diaryId and e.status = :status")
    int deleteByDiaryIdAndStatus(@Param("diaryId") Long diaryId, @Param("status") ItemStatus status);
}
