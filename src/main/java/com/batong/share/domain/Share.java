package com.batong.share.domain;

import com.batong.global.enums.DocumentType;
import com.batong.share.dto.ShareDtos.PublicContent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** share. approved_at이 NULL이면 초안이다. 승인·철회 컬럼(token_hash 등)은 SHARE-03·05 구현에서 매핑한다. */
@Getter
@Entity
@Table(name = "share")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_id")
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private DocumentType documentType;

    @Column(name = "recipient_label", length = 30)
    private String recipientLabel;

    @Column(name = "source_diary_id")
    private Long sourceDiaryId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "show_emergency_contact", nullable = false)
    private boolean showEmergencyContact;

    /** 승인 시점 고정본. 초안 단계에서 만든 미리보기가 그대로 들어간다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json")
    private PublicContent snapshot;

    @ElementCollection
    @CollectionTable(name = "share_section", joinColumns = @JoinColumn(name = "share_id"))
    @Column(name = "section_code", length = 30)
    private Set<String> sectionCodes = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Share draft(Long subjectId, Long createdBy, DocumentType documentType, String recipientLabel,
                              Long sourceDiaryId, boolean showEmergencyContact, Set<String> sectionCodes,
                              PublicContent snapshot) {
        Share s = new Share();
        s.subjectId = subjectId;
        s.createdBy = createdBy;
        s.documentType = documentType;
        s.recipientLabel = recipientLabel;
        s.sourceDiaryId = sourceDiaryId;
        s.showEmergencyContact = showEmergencyContact;
        s.sectionCodes = new LinkedHashSet<>(sectionCodes);
        s.snapshot = snapshot;
        s.createdAt = Instant.now();
        return s;
    }
}
