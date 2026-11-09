package com.batong.subject.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.enums.ConsentType;
import com.batong.global.enums.GuideSection;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.guide.domain.GuideEntry;
import com.batong.guide.repository.GuideEntryRepository;
import com.batong.subject.domain.CareSubject;
import com.batong.subject.dto.SubjectDtos.ConsentInput;
import com.batong.subject.dto.SubjectDtos.OnboardingRequest;
import com.batong.subject.dto.SubjectDtos.OnboardingResponse;
import com.batong.subject.dto.SubjectDtos.Profile;
import com.batong.subject.dto.SubjectDtos.SubjectInput;
import com.batong.subject.dto.SubjectDtos.SubjectResponse;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateRequest;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateResponse;
import com.batong.subject.repository.CareSubjectRepository;
import com.batong.user.dto.TermsResponse.Term;
import com.batong.user.dto.UserDtos.ProfileUpdateRequest;
import com.batong.user.service.TermsService;
import com.batong.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** ONB-01, SUBJ-02·03 */
@Service
@RequiredArgsConstructor
public class SubjectService {

    /** guide_entry.content VARCHAR(500). GUIDE-02와 같은 상한 */
    private static final int MAX_ANSWER = 500;

    private final CareSubjectRepository careSubjectRepository;
    private final SubjectRoleResolver roleResolver;
    private final UserService userService;
    private final TermsService termsService;
    private final GuideEntryRepository guideEntryRepository;
    private final JdbcClient jdbc;

    /** 0.2-3 질문 → 설명서 섹션 (ERD 설명서 섹션 표, PRD 0.2-3) */
    private record Answer(String questionCode, GuideSection section, String content) { }

    /**
     * ONB-01. 한 트랜잭션에서 보호자 이름·관계, 대상자, 동의, 초기 설명서 항목을 저장한다. 하나라도 실패하면 모두 되돌린다.
     * 이미 소유한 대상자가 있으면 입력값보다 먼저 409. users 행을 잠가 동시에 두 번 보내도 대상자는 하나만 생긴다.
     */
    @Transactional
    public OnboardingResponse onboard(Long userId, OnboardingRequest request) {
        jdbc.sql("SELECT user_id FROM users WHERE user_id = :u FOR UPDATE").param("u", userId)
                .query(Long.class).optional().orElseThrow(() -> new BusinessException(ErrorCode.COMMON_UNAUTHORIZED));
        boolean owns = jdbc.sql("SELECT EXISTS (SELECT 1 FROM care_subject WHERE owner_user_id = :u)")
                .param("u", userId).query(Boolean.class).single();
        if (owns) {
            throw new BusinessException(ErrorCode.ONB_ALREADY_COMPLETED);
        }
        if (request == null || request.guardian() == null || request.subject() == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        List<Answer> answers = answers(request.profile());
        Set<ConsentType> consents = requireConsents(request.consents());

        userService.updateProfile(userId,
                new ProfileUpdateRequest(request.guardian().displayName(), request.guardian().relation()));
        SubjectInput in = request.subject();
        CareSubject subject = careSubjectRepository.save(
                CareSubject.create(userId, in.name(), in.birthYear(), in.district(), in.dong()));

        Timestamp now = Timestamp.from(Instant.now());
        for (ConsentType type : consents) {
            jdbc.sql("""
                    INSERT INTO consent (user_id, subject_id, consent_type, terms_version, agreed_at)
                    VALUES (:u, :s, :t, :v, :now)""")
                    .param("u", userId).param("s", subject.getId()).param("t", type.name())
                    .param("v", TermsService.CURRENT_VERSION).param("now", now).update();
        }
        for (Answer a : answers) {
            guideEntryRepository.save(GuideEntry.onboarding(subject.getId(), a.section(), a.content(), a.questionCode()));
        }
        return new OnboardingResponse(subject.getId(), answers.size());
    }

    /** null·빈 답변은 항목을 만들지 않는다(PRD 0.2-3). 앞뒤 공백을 지우고 500자 초과나 저장할 수 없는 문자는 COMMON_VALIDATION */
    private static List<Answer> answers(Profile profile) {
        if (profile == null) {
            return List.of();
        }
        List<Answer> result = new ArrayList<>();
        addAnswer(result, "Q1", GuideSection.DAILY_LIFE, profile.q1());
        addAnswer(result, "Q2", GuideSection.MEDICATION, profile.q2());
        addAnswer(result, "Q3", GuideSection.BEHAVIOR_RESPONSE, profile.q3());
        addAnswer(result, "Q4", GuideSection.BEHAVIOR_RESPONSE, profile.q4());
        addAnswer(result, "Q5", GuideSection.COMMUNICATION, profile.q5());
        return result;
    }

    private static void addAnswer(List<Answer> result, String code, GuideSection section, String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty()) {
            return;
        }
        if (content.codePointCount(0, content.length()) > MAX_ANSWER || !storable(content)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        result.add(new Answer(code, section, content));
    }

    /** TERMS-01의 필수 항목이 모두 현재 버전으로 와야 한다. 같은 항목이 두 번 오면 한 번만 저장한다 */
    private Set<ConsentType> requireConsents(List<ConsentInput> inputs) {
        Set<ConsentType> agreed = EnumSet.noneOf(ConsentType.class);
        if (inputs != null) {
            for (ConsentInput c : inputs) {
                if (c != null && c.consentType() != null && TermsService.CURRENT_VERSION.equals(c.termsVersion())) {
                    agreed.add(c.consentType());
                }
            }
        }
        for (Term term : termsService.getTerms().terms()) {
            if (term.required() && !agreed.contains(term.consentType())) {
                throw new BusinessException(ErrorCode.SUBJECT_CONSENT_REQUIRED);
            }
        }
        return agreed;
    }

    /** NUL은 PostgreSQL이 저장하지 못하고, 짝 없는 서로게이트는 '?'로 바뀐다 */
    private static boolean storable(String s) {
        return s.codePoints().noneMatch(c -> c == 0 || (c >= 0xD800 && c <= 0xDFFF));
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
