package com.batong.share.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.enums.DocumentType;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.ItemCategory;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.share.domain.Share;
import com.batong.share.repository.ShareRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.batong.global.StubData.at;

/** SHARE-01·02 구현. SHARE-03~05, PUB-01은 #13~#15 전까지 스텁 */
@Service
public class ShareService {

    private static final SharedBasicInfo BASIC_INFO = new SharedBasicInfo("민지", 25, "어머니 010-0000-0000");

    private static final int MAX_LABEL = 30;
    private static final int VALID_DAYS = 7;

    private final String shareBaseUrl;
    private final SubjectRoleResolver roles;
    private final JdbcClient jdbc;
    private final ShareRepository shares;

    public ShareService(@Value("${app.share.base-url}") String shareBaseUrl, SubjectRoleResolver roles,
                        JdbcClient jdbc, ShareRepository shares) {
        this.shareBaseUrl = shareBaseUrl;
        this.roles = roles;
        this.jdbc = jdbc;
        this.shares = shares;
    }

    public SectionOptionsResponse getSectionOptions(Long subjectId, Long userId, DocumentType documentType,
                                                    Long sourceDiaryId, LocalDate periodStart) {
        roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        Map<GuideSection, List<String>> content = contentOf(subjectId, documentType, sourceDiaryId);
        List<SectionOption> options = Arrays.stream(GuideSection.values())
                .map(sec -> {
                    boolean has = !content.get(sec).isEmpty();
                    return new SectionOption(sec.name(), has, has);
                }).toList();
        return new SectionOptionsResponse(documentType, options,
                new EmergencyContactOption(subject(subjectId).emergencyContact() != null, false));
    }

    @Transactional
    public DraftResponse createDraft(Long subjectId, Long userId, DraftRequest request) {
        roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        if (request == null || request.documentType() == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        Map<GuideSection, List<String>> content = contentOf(subjectId, request.documentType(), request.sourceDiaryId());

        if (request.sectionCodes() == null || request.sectionCodes().isEmpty()) {
            throw new BusinessException(ErrorCode.SHARE_NO_SECTION);
        }
        Set<GuideSection> selected = EnumSet.noneOf(GuideSection.class);
        for (String code : request.sectionCodes()) {
            if (!selected.add(parseSection(code))) {
                throw new BusinessException(ErrorCode.COMMON_VALIDATION);
            }
        }
        // 내용이 없는 섹션은 선택할 수 없다(기획서 4.1)
        if (selected.stream().anyMatch(sec -> content.get(sec).isEmpty())) {
            throw new BusinessException(ErrorCode.SHARE_NO_APPROVED_CONTENT);
        }
        String label = request.recipientLabel() == null ? null : request.recipientLabel().strip();
        if (label != null && label.length() > MAX_LABEL) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        if (label != null && label.isEmpty()) {
            label = null;
        }

        SubjectRow subject = subject(subjectId);
        // 고정본의 기본 정보는 이름·나이만, 비상 연락처는 명시적으로 켠 경우에만(지역·출생 연도 제외)
        SharedBasicInfo basic = new SharedBasicInfo(subject.name(), subject.age(),
                request.showEmergencyContact() ? subject.emergencyContact() : null);
        List<SharedSection> sections = selected.stream()
                .map(sec -> new SharedSection(sec.name(), content.get(sec))).toList();
        PublicContent snapshot = new PublicContent(basic, List.of(), sections);

        Share share = shares.save(Share.draft(subjectId, userId, request.documentType(), label,
                request.documentType() == DocumentType.DAILY ? request.sourceDiaryId() : null,
                request.showEmergencyContact(),
                selected.stream().map(GuideSection::name).collect(Collectors.toCollection(LinkedHashSet::new)),
                snapshot));
        return new DraftResponse(share.getId(),
                new Preview(basic, request.documentType(), snapshot.summaryLines(), sections), VALID_DAYS);
    }

    /** 문서 유형별로 공유할 수 있는 내용을 섹션별로 모은다. 항상 GuideSection 7개 키를 가진다. */
    private Map<GuideSection, List<String>> contentOf(Long subjectId, DocumentType type, Long sourceDiaryId) {
        Map<GuideSection, List<String>> result = new EnumMap<>(GuideSection.class);
        Arrays.stream(GuideSection.values()).forEach(sec -> result.put(sec, new ArrayList<>()));
        if (type == DocumentType.GUIDE) {
            jdbc.sql("SELECT section, content FROM guide_entry WHERE subject_id = :s ORDER BY entry_id")
                    .param("s", subjectId)
                    .query((rs, n) -> Map.entry(GuideSection.valueOf(rs.getString("section")), rs.getString("content")))
                    .list().forEach(e -> result.get(e.getKey()).add(e.getValue()));
        } else if (type == DocumentType.DAILY) {
            if (sourceDiaryId == null) {
                throw new BusinessException(ErrorCode.SHARE_SOURCE_REQUIRED);
            }
            // 이 대상자의 일기여야 하고, 승인(APPROVED)된 항목만 쓴다. 원문은 읽지 않는다.
            jdbc.sql("""
                            SELECT e.category, e.content FROM extracted_item e
                              JOIN diary d ON d.diary_id = e.diary_id
                             WHERE d.diary_id = :d AND d.subject_id = :s AND e.status = 'APPROVED'
                             ORDER BY e.item_no""")
                    .param("d", sourceDiaryId).param("s", subjectId)
                    .query((rs, n) -> Map.entry(ItemCategory.valueOf(rs.getString("category")).getSection(),
                            rs.getString("content")))
                    .list().forEach(e -> result.get(e.getKey()).add(e.getValue()));
            boolean ownDiary = jdbc.sql("SELECT count(*) FROM diary WHERE diary_id = :d AND subject_id = :s")
                    .param("d", sourceDiaryId).param("s", subjectId).query(Integer.class).single() > 0;
            if (!ownDiary) {
                throw new BusinessException(ErrorCode.SHARE_SOURCE_REQUIRED);
            }
        } else {
            // ponytail: WEEKLY는 주간 리포트(REPORT-03, P1)가 생기면 추가한다
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        return result;
    }

    private static GuideSection parseSection(String code) {
        try {
            return GuideSection.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
    }

    private record SubjectRow(String name, int age, String emergencyContact) {
    }

    private SubjectRow subject(Long subjectId) {
        return jdbc.sql("SELECT name, birth_year, emergency_contact FROM care_subject WHERE subject_id = :s")
                .param("s", subjectId)
                .query((rs, n) -> new SubjectRow(rs.getString("name"),
                        Year.now(ZoneId.of("Asia/Seoul")).getValue() - rs.getInt("birth_year"),
                        blankToNull(rs.getString("emergency_contact"))))
                .single();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
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
