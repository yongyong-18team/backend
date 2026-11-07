package com.batong.subject.service;

import com.batong.global.enums.District;
import com.batong.subject.dto.SubjectDtos.OnboardingRequest;
import com.batong.subject.dto.SubjectDtos.OnboardingResponse;
import com.batong.subject.dto.SubjectDtos.SubjectResponse;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateRequest;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateResponse;
import org.springframework.stereotype.Service;

import static com.batong.global.StubData.at;

/** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #22(ONB-01), #23(SUBJ-02·03) */
@Service
public class SubjectService {

    public OnboardingResponse onboard(Long userId, OnboardingRequest request) {
        return new OnboardingResponse(10L, 4);
    }

    public SubjectResponse getSubject(Long subjectId, Long userId) {
        return new SubjectResponse(subjectId, "민지", 2001, 25, District.SUJI, "풍덕천동", "어머니 010-0000-0000");
    }

    public SubjectUpdateResponse updateSubject(Long subjectId, Long userId, SubjectUpdateRequest request) {
        return new SubjectUpdateResponse(subjectId, at("2026-11-03T21:00:00+09:00"));
    }
}
