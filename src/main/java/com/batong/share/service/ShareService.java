package com.batong.share.service;

import com.batong.global.enums.DocumentType;
import com.batong.global.enums.ShareStatus;
import com.batong.share.dto.ShareDtos.ApproveResponse;
import com.batong.share.dto.ShareDtos.DraftRequest;
import com.batong.share.dto.ShareDtos.DraftResponse;
import com.batong.share.dto.ShareDtos.EmergencyContactOption;
import com.batong.share.dto.ShareDtos.Preview;
import com.batong.share.dto.ShareDtos.PublicContent;
import com.batong.share.dto.ShareDtos.PublicShareResponse;
import com.batong.share.dto.ShareDtos.RevokeResponse;
import com.batong.share.dto.ShareDtos.SectionOption;
import com.batong.share.dto.ShareDtos.SectionOptionsResponse;
import com.batong.share.dto.ShareDtos.ShareListResponse;
import com.batong.share.dto.ShareDtos.ShareSummary;
import com.batong.share.dto.ShareDtos.SharedBasicInfo;
import com.batong.share.dto.ShareDtos.SharedSection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

import static com.batong.global.StubData.at;

/** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #12~#15 */
@Service
public class ShareService {

    private static final SharedBasicInfo BASIC_INFO = new SharedBasicInfo("민지", 25, "어머니 010-0000-0000");

    private final String shareBaseUrl;

    public ShareService(@Value("${app.share.base-url}") String shareBaseUrl) {
        this.shareBaseUrl = shareBaseUrl;
    }

    public SectionOptionsResponse getSectionOptions(Long subjectId, Long userId, DocumentType documentType,
                                                    Long sourceDiaryId, LocalDate periodStart) {
        return new SectionOptionsResponse(DocumentType.GUIDE,
                List.of(new SectionOption("BEHAVIOR_RESPONSE", true, true),
                        new SectionOption("HEALTH_RISK", true, false)),
                new EmergencyContactOption(true, false));
    }

    public DraftResponse createDraft(Long subjectId, Long userId, DraftRequest request) {
        return new DraftResponse(900L,
                new Preview(BASIC_INFO, DocumentType.GUIDE, List.of(),
                        List.of(new SharedSection("BEHAVIOR_RESPONSE", List.of("공사 소음에 불안해함")))),
                7);
    }

    public ApproveResponse approve(Long subjectId, Long shareId, Long userId) {
        return new ApproveResponse(shareId, shareBaseUrl + "/s/stub-token-for-frontend", DocumentType.GUIDE,
                at("2026-11-03T21:30:00+09:00"), at("2026-11-10T21:30:00+09:00"), "이 링크는 지금만 복사할 수 있어요.");
    }

    public ShareListResponse getShares(Long subjectId, Long userId) {
        return new ShareListResponse(List.of(new ShareSummary(900L, DocumentType.GUIDE, "목요일 선생님", "김영희",
                at("2026-11-03T21:30:00+09:00"), at("2026-11-10T21:30:00+09:00"), ShareStatus.ACTIVE, true)));
    }

    public RevokeResponse revoke(Long subjectId, Long shareId, Long userId) {
        return new RevokeResponse(shareId, ShareStatus.REVOKED);
    }

    public PublicShareResponse getPublicShare(String token) {
        return new PublicShareResponse(DocumentType.GUIDE, at("2026-11-03T21:30:00+09:00"), at("2026-11-10T21:30:00+09:00"),
                new PublicContent(BASIC_INFO, List.of(),
                        List.of(new SharedSection("BEHAVIOR_RESPONSE", List.of("공사 소음에 불안해함")))));
    }
}
