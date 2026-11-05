package com.batong.share.dto;

import com.batong.global.enums.DocumentType;
import com.batong.global.enums.ShareStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** SHARE-01~05, PUB-01 요청·응답. sectionCode는 설명서 7개 섹션 또는 WEEKLY_SUMMARY·DAILY_TREND·KEY_CHANGES */
public final class ShareDtos {

    private ShareDtos() {
    }

    public record SectionOptionsResponse(DocumentType documentType, List<SectionOption> options,
                                         EmergencyContactOption emergencyContact) {
    }

    public record SectionOption(String sectionCode, boolean defaultOn, boolean hasContent) {
    }

    public record EmergencyContactOption(boolean available, boolean defaultOn) {
    }

    /** WEEKLY는 periodStart, DAILY는 sourceDiaryId 필수 */
    public record DraftRequest(DocumentType documentType, Long sourceDiaryId, LocalDate periodStart,
                               List<String> sectionCodes, boolean showEmergencyContact, String recipientLabel) {
    }

    public record DraftResponse(Long shareId, Preview preview, int validDays) {
    }

    public record Preview(SharedBasicInfo basicInfo, DocumentType documentType, List<String> summaryLines,
                          List<SharedSection> sections) {
    }

    /** 고정본의 기본 정보는 이름·나이만, 비상 연락처는 showEmergencyContact일 때만 */
    public record SharedBasicInfo(String name, int age, String emergencyContact) {
    }

    public record SharedSection(String section, List<String> entries) {
    }

    /** shareUrl은 이 응답에서만 제공 */
    public record ApproveResponse(Long shareId, String shareUrl, DocumentType documentType,
                                  Instant approvedAt, Instant expiresAt, String notice) {
    }

    public record ShareListResponse(List<ShareSummary> shares) {
    }

    public record ShareSummary(Long shareId, DocumentType documentType, String recipientLabel, String createdByName,
                               Instant approvedAt, Instant expiresAt, ShareStatus status, boolean revocable) {
    }

    public record RevokeResponse(Long shareId, ShareStatus status) {
    }

    public record PublicShareResponse(DocumentType documentType, Instant sharedAt, Instant expiresAt,
                                      PublicContent content) {
    }

    public record PublicContent(SharedBasicInfo basicInfo, List<String> summaryLines, List<SharedSection> sections) {
    }
}
