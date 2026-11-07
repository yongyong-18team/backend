package com.batong.analysis.domain;

import com.batong.global.enums.SafetyLevel;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * AI 분석 결과 (ERD 3-2 ai_analysis, DIARY의 약한 개체). 재분석마다 analysisNo가 1씩 오른다.
 * raw_json은 어떤 API 응답에도 넣지 않는다.
 * ID를 직접 넣으므로 {@link Persistable}로 새 엔티티임을 알려 save()가 merge(추가 SELECT)를 하지 않게 한다.
 */
@Getter
@Entity
@Table(name = "ai_analysis")
@IdClass(AiAnalysis.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAnalysis implements Persistable<AiAnalysis.Key> {

    /** LLM이 실패했는데 키워드만으로 위기를 판정한 경우의 prompt_version (AI 명세 2-5) */
    public static final String KEYWORD_ONLY = "keyword-only";

    @Id
    @Column(name = "diary_id")
    private Long diaryId;

    /** DB가 SMALLINT라 ddl-auto=validate에 맞춰 Short */
    @Id
    @Column(name = "analysis_no")
    private Short analysisNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_level", nullable = false, length = 10)
    private SafetyLevel safetyLevel;

    @Column(name = "empathy_text", length = 300)
    private String empathyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json")
    private String rawJson;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "model_name", length = 50)
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public static AiAnalysis normal(Long diaryId, int analysisNo, String empathyText, String rawJson,
                                    String promptVersion, String modelName) {
        return create(diaryId, analysisNo, SafetyLevel.NORMAL, empathyText, rawJson, promptVersion, modelName);
    }

    /** 위기면 공감 문장을 저장하지 않는다(ERD CHECK). LLM 없이 키워드로만 판정했으면 rawJson·modelName은 null */
    public static AiAnalysis crisis(Long diaryId, int analysisNo, String rawJson, String promptVersion,
                                    String modelName) {
        return create(diaryId, analysisNo, SafetyLevel.CRISIS, null, rawJson, promptVersion, modelName);
    }

    private static AiAnalysis create(Long diaryId, int analysisNo, SafetyLevel safetyLevel, String empathyText,
                                     String rawJson, String promptVersion, String modelName) {
        AiAnalysis a = new AiAnalysis();
        a.diaryId = diaryId;
        a.analysisNo = (short) analysisNo;
        a.safetyLevel = safetyLevel;
        a.empathyText = empathyText;
        a.rawJson = rawJson;
        a.promptVersion = promptVersion;
        a.modelName = modelName;
        a.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return a;
    }

    @Override
    public Key getId() {
        return new Key(diaryId, analysisNo);
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
        private Short analysisNo;

        public Key(Long diaryId, Short analysisNo) {
            this.diaryId = diaryId;
            this.analysisNo = analysisNo;
        }
    }
}
