package com.batong.subject.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.subject.domain.CareSubject;
import com.batong.subject.dto.SubjectDtos.OnboardingRequest;
import com.batong.subject.dto.SubjectDtos.OnboardingResponse;
import com.batong.subject.dto.SubjectDtos.SubjectResponse;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateRequest;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateResponse;
import com.batong.subject.repository.CareSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SUBJ-02·03. ONB-01은 아직 스텁(#22) */
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final CareSubjectRepository careSubjectRepository;
    private final SubjectRoleResolver roleResolver;

    /** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #22 */
    public OnboardingResponse onboard(Long userId, OnboardingRequest request) {
        return new OnboardingResponse(10L, 4);
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubject(Long subjectId, Long userId) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        CareSubject subject = find(subjectId);
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getBirthYear(), subject.age(),
                subject.getDistrict(), subject.getDong(), subject.getEmergencyContact());
    }

    @Transactional
    public SubjectUpdateResponse updateSubject(Long subjectId, Long userId, SubjectUpdateRequest request) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        CareSubject subject = find(subjectId);
        subject.update(request.name(), request.birthYear(), request.district(), request.dong(),
                request.emergencyContact());
        return new SubjectUpdateResponse(subject.getId(), subject.getUpdatedAt());
    }

    /** require()를 통과했으면 있는 대상자다. 그 사이 삭제됐으면 존재 여부를 숨기려고 같은 403 */
    private CareSubject find(Long subjectId) {
        return careSubjectRepository.findById(subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_FORBIDDEN));
    }
}
