package com.batong.subject.dto;

import com.batong.global.enums.ConsentType;
import com.batong.global.enums.District;
import com.batong.global.enums.Relation;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** ONB-01, SUBJ-02·03 요청·응답 */
public final class SubjectDtos {

    private SubjectDtos() {
    }

    public record OnboardingRequest(Guardian guardian, SubjectInput subject, Profile profile, List<ConsentInput> consents) {
    }

    public record Guardian(String displayName, Relation relation) {
    }

    public record SubjectInput(String name, Integer birthYear, District district, String dong) {
    }

    /** 0.2-3 질문 답변. null이거나 빈 답변은 설명서 항목을 만들지 않는다. JSON 키는 명세대로 Q1~Q5 */
    public record Profile(@JsonProperty("Q1") String q1, @JsonProperty("Q2") String q2, @JsonProperty("Q3") String q3,
                          @JsonProperty("Q4") String q4, @JsonProperty("Q5") String q5) {
    }

    public record ConsentInput(ConsentType consentType, String termsVersion) {
    }

    public record OnboardingResponse(Long subjectId, int createdGuideEntryCount) {
    }

    /** age는 birthYear로 계산한 값 */
    public record SubjectResponse(Long subjectId, String name, int birthYear, int age, District district, String dong,
                                  String emergencyContact) {
    }

    /** 바꿀 값만 보낸다 */
    public record SubjectUpdateRequest(String name, Integer birthYear, District district, String dong,
                                       String emergencyContact) {
    }

    public record SubjectUpdateResponse(Long subjectId, Instant updatedAt) {
    }
}
