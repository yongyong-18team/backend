package com.batong.resource.domain;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.ProgramType;
import com.batong.global.enums.ResourceKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 지원제도 (ERD support_program). 시드(R__seed_support_program.sql)로 들어가고 서버는 읽기만 한다 */
@Getter
@Entity
@Table(name = "support_program")
@PrimaryKeyJoinColumn(name = "resource_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportProgram extends Resource {

    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false, length = 30)
    private ProgramType programType;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_term", nullable = false, length = 10)
    private CareTerm careTerm;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "eligibility_summary", columnDefinition = "text")
    private String eligibilitySummary;

    /** 매칭 규칙. 키는 minAge(이상), maxAge(미만). 키가 없으면 그 조건은 보지 않는다 (#84) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_conditions", nullable = false)
    private Map<String, Object> targetConditions;

    @Column(columnDefinition = "text")
    private String procedure;

    @Column(name = "source_doc", length = 200)
    private String sourceDoc;

    @Column(name = "doc_version", length = 20)
    private String docVersion;

    /** program_facility_type. 이 제도와 관련 시설로 연결된 시설 유형 */
    @ElementCollection
    @CollectionTable(name = "program_facility_type", joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "facility_type", length = 50)
    private Set<String> facilityTypes = new HashSet<>();

    /** 테스트용. 운영 데이터는 시드 SQL로만 넣는다 */
    public static SupportProgram create(String name, String phone, String officialUrl, ProgramType programType,
                                        CareTerm careTerm, String summary, String eligibilitySummary,
                                        Map<String, Object> targetConditions, String procedure, String sourceDoc,
                                        String docVersion, Set<String> facilityTypes) {
        SupportProgram p = new SupportProgram();
        p.initBase(ResourceKind.PROGRAM, name, phone, officialUrl);
        p.programType = programType;
        p.careTerm = careTerm;
        p.summary = summary;
        p.eligibilitySummary = eligibilitySummary;
        p.targetConditions = targetConditions;
        p.procedure = procedure;
        p.sourceDoc = sourceDoc;
        p.docVersion = docVersion;
        p.facilityTypes = new HashSet<>(facilityTypes);
        return p;
    }
}
