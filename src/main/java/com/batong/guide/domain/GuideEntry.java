package com.batong.guide.domain;

import com.batong.global.enums.GuideSection;
import com.batong.global.enums.SourceType;
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

/** guide_entry. 출처 컬럼(src_*)은 ITEM-01·온보딩 구현에서 필요할 때 매핑한다. */
@Getter
@Entity
@Table(name = "guide_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuideEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuideSection section;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 12)
    private SourceType sourceType;

    @Column(name = "src_question_code", length = 5)
    private String srcQuestionCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static GuideEntry manual(Long subjectId, GuideSection section, String content) {
        GuideEntry e = new GuideEntry();
        e.subjectId = subjectId;
        e.section = section;
        e.content = content;
        e.sourceType = SourceType.MANUAL;
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        return e;
    }

    /** 온보딩 Q1~Q5 답변 하나가 항목 하나가 된다(ONB-01). questionCode는 "Q1"~"Q5" */
    public static GuideEntry onboarding(Long subjectId, GuideSection section, String content, String questionCode) {
        GuideEntry e = manual(subjectId, section, content);
        e.sourceType = SourceType.ONBOARDING;
        e.srcQuestionCode = questionCode;
        return e;
    }

    public void changeContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }
}
