package com.batong.user.dto;

import com.batong.global.enums.ConsentType;

import java.util.List;

/** TERMS-01 응답 */
public record TermsResponse(List<Term> terms) {

    public record Term(ConsentType consentType, String termsVersion, boolean required, String title, String content) {
    }
}
