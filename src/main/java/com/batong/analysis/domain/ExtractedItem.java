package com.batong.analysis.domain;

import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.ItemStatus;
import com.batong.global.enums.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

/**
 * AI가 일기에서 뽑은 항목 (ERD 3-2 extracted_item, DIARY의 약한 개체). 보호자가 검토해 승인(ITEM-01)하면 설명서에 반영된다.
 * content·evidenceQuote는 이름을 되돌린 문장이다(AI 명세 2-3-4). 섹션은 저장하지 않고 category로 계산한다.
 */
@Getter
@Entity
@Table(name = "extracted_item")
@IdClass(ExtractedItem.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractedItem implements Persistable<ExtractedItem.Key> {

    @Id
    @Column(name = "diary_id")
    private Long diaryId;

    /** DB가 SMALLINT라 Short. 재분석해도 기존 최댓값 다음부터 매긴다(TRD 8-5) */
    @Id
    @Column(name = "item_no")
    private Short itemNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemCategory category;

    /** BEHAVIOR만 BehaviorLabel의 한글 text, 그 외 null */
    @Column(length = 30)
    private String label;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", length = 10)
    private TimeSlot timeSlot;

    @Column(name = "duration_min")
    private Short durationMin;

    @Column(name = "episode_no")
    private Short episodeNo;

    @Column(name = "evidence_quote", nullable = false, length = 500)
    private String evidenceQuote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemStatus status;

    @Column(nullable = false)
    private boolean edited;

    @Transient
    private boolean isNew = true;

    /** 분석 직후는 검토 대기(PENDING) */
    public static ExtractedItem pending(Long diaryId, int itemNo, ItemCategory category, String label,
                                        String content, TimeSlot timeSlot, Integer durationMin, Integer episodeNo,
                                        String evidenceQuote) {
        ExtractedItem item = new ExtractedItem();
        item.diaryId = diaryId;
        item.itemNo = (short) itemNo;
        item.category = category;
        item.label = label;
        item.content = content;
        item.timeSlot = timeSlot;
        item.durationMin = durationMin == null ? null : durationMin.shortValue();
        item.episodeNo = episodeNo == null ? null : episodeNo.shortValue();
        item.evidenceQuote = evidenceQuote;
        item.status = ItemStatus.PENDING;
        item.edited = false;
        return item;
    }

    @Override
    public Key getId() {
        return new Key(diaryId, itemNo);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long diaryId;
        private Short itemNo;

        public Key(Long diaryId, Short itemNo) {
            this.diaryId = diaryId;
            this.itemNo = itemNo;
        }
    }
}
