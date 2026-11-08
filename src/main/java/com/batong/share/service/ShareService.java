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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** SHARE-01~05, PUB-01 */
@Service
public class ShareService {

    private static final int MAX_LABEL = 30;
    private static final int VALID_DAYS = 7;

    private final String shareBaseUrl;
    private final SubjectRoleResolver roles;
    private final JdbcClient jdbc;
    private final ShareRepository shares;
    private final ObjectMapper objectMapper;

    public ShareService(@Value("${app.share.base-url}") String shareBaseUrl, SubjectRoleResolver roles,
                        JdbcClient jdbc, ShareRepository shares, ObjectMapper objectMapper) {
        this.shareBaseUrl = shareBaseUrl;
        this.roles = roles;
        this.jdbc = jdbc;
        this.shares = shares;
        this.objectMapper = objectMapper;
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
        if (label != null && (label.length() > MAX_LABEL || !storable(label))) {
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

    /** PostgreSQL은 NUL을 저장하지 못하고, 짝 없는 서로게이트는 저장할 때 조용히 '?'로 바뀐다. */
    private static boolean storable(String s) {
        return s.indexOf('\u0000') < 0 && s.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF);
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

    @Transactional
    public ApproveResponse approve(Long subjectId, Long shareId, Long userId) {
        roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        record Row(Long createdBy, DocumentType type) { }
        Row row = jdbc.sql("SELECT created_by, document_type FROM share WHERE share_id = :i AND subject_id = :s")
                .param("i", shareId).param("s", subjectId)
                .query((rs, n) -> new Row(rs.getObject("created_by", Long.class),
                        DocumentType.valueOf(rs.getString("document_type"))))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (!userId.equals(row.createdBy())) {
            throw new BusinessException(ErrorCode.SHARE_FORBIDDEN);
        }
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(VALID_DAYS, ChronoUnit.DAYS);
        // approved_at IS NULL 조건으로 동시 승인 중 하나만 갱신된다
        int updated = jdbc.sql("""
                UPDATE share SET token_hash = :h, approved_at = :a, expires_at = :e
                WHERE share_id = :i AND approved_at IS NULL""")
                .param("h", sha256Hex(token)).param("a", Timestamp.from(now)).param("e", Timestamp.from(expiresAt))
                .param("i", shareId).update();
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SHARE_ALREADY_APPROVED);
        }
        return new ApproveResponse(shareId, shareBaseUrl + "/s/" + token, row.type(), now, expiresAt,
                "이 링크는 지금만 복사할 수 있어요.");
    }

    private static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public ShareListResponse getShares(Long subjectId, Long userId) {
        SubjectRole role = roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        Instant now = Instant.now();
        List<ShareSummary> list = jdbc.sql("""
                SELECT s.share_id, s.document_type, s.recipient_label, s.created_by, u.display_name,
                       s.approved_at, s.expires_at, s.revoked_at
                FROM share s LEFT JOIN users u ON u.user_id = s.created_by
                WHERE s.subject_id = :s AND s.approved_at IS NOT NULL
                ORDER BY s.approved_at DESC, s.share_id DESC""")
                .param("s", subjectId)
                .query((rs, n) -> {
                    Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                    ShareStatus status = rs.getTimestamp("revoked_at") != null ? ShareStatus.REVOKED
                            : !expiresAt.isAfter(now) ? ShareStatus.EXPIRED : ShareStatus.ACTIVE;
                    Long createdBy = rs.getObject("created_by", Long.class);
                    boolean revocable = status == ShareStatus.ACTIVE
                            && (role == SubjectRole.OWNER || userId.equals(createdBy));
                    return new ShareSummary(rs.getLong("share_id"), DocumentType.valueOf(rs.getString("document_type")),
                            rs.getString("recipient_label"), rs.getString("display_name"),
                            rs.getTimestamp("approved_at").toInstant(), expiresAt, status, revocable);
                }).list();
        return new ShareListResponse(list);
    }

    @Transactional
    public RevokeResponse revoke(Long subjectId, Long shareId, Long userId) {
        SubjectRole role = roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        record Row(Long createdBy) { }
        Row row = jdbc.sql("SELECT created_by FROM share WHERE share_id = :i AND subject_id = :s AND approved_at IS NOT NULL")
                .param("i", shareId).param("s", subjectId)
                .query((rs, n) -> new Row(rs.getObject("created_by", Long.class)))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (role != SubjectRole.OWNER && !userId.equals(row.createdBy())) {
            throw new BusinessException(ErrorCode.SHARE_FORBIDDEN);
        }
        int updated = jdbc.sql("""
                UPDATE share SET revoked_at = :now
                WHERE share_id = :i AND revoked_at IS NULL AND expires_at > :now""")
                .param("now", Timestamp.from(Instant.now())).param("i", shareId).update();
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SHARE_ALREADY_INACTIVE);
        }
        return new RevokeResponse(shareId, ShareStatus.REVOKED);
    }

    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    public PublicShareResponse getPublicShare(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        record Row(DocumentType type, Instant approvedAt, Instant expiresAt, boolean revoked, PublicContent content) { }
        Row row = jdbc.sql("SELECT document_type, approved_at, expires_at, revoked_at, snapshot_json FROM share WHERE token_hash = :h")
                .param("h", sha256Hex(token))
                .query((rs, n) -> new Row(DocumentType.valueOf(rs.getString("document_type")),
                        rs.getTimestamp("approved_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") != null, parseSnapshot(rs.getString("snapshot_json"))))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (row.revoked()) {
            throw new BusinessException(ErrorCode.SHARE_REVOKED);
        }
        if (!row.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }
        return new PublicShareResponse(row.type(), row.approvedAt(), row.expiresAt(), row.content());
    }

    private PublicContent parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, PublicContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("share snapshot 파싱 실패", e);
        }
    }
}
