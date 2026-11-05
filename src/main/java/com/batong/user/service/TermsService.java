package com.batong.user.service;

import com.batong.global.enums.ConsentType;
import com.batong.user.dto.TermsResponse;
import com.batong.user.dto.TermsResponse.Term;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 동의 항목은 테이블 없이 코드로 관리한다(ERD에 약관 테이블 없음). 버전을 올리면 consent에 새 버전 행이 쌓인다.
 * ONB-01에서 필수 동의·현재 버전을 확인할 때도 이 목록을 쓴다.
 */
@Service
public class TermsService {

    public static final String CURRENT_VERSION = "1.0";

    // ponytail: 약관 본문은 기획 미결(API 명세 5장 #2). 확정되면 content만 바꾼다
    private static final String CONTENT_PENDING = "약관 본문을 준비하고 있어요.";

    private static final List<Term> TERMS = List.of(
            new Term(ConsentType.PRIVACY, CURRENT_VERSION, true, "개인정보 수집·이용", CONTENT_PENDING),
            new Term(ConsentType.SENSITIVE_INFO, CURRENT_VERSION, true, "민감정보 처리", CONTENT_PENDING),
            new Term(ConsentType.AI_PROCESSING, CURRENT_VERSION, true, "AI 기반 정보 처리 안내", CONTENT_PENDING),
            new Term(ConsentType.SUBJECT_AUTHORITY, CURRENT_VERSION, true, "돌봄 대상자 정보 처리 권한 확인", CONTENT_PENDING)
    );

    public TermsResponse getTerms() {
        return new TermsResponse(TERMS);
    }
}
