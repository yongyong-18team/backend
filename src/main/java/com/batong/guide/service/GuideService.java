package com.batong.guide.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.enums.District;
import com.batong.global.enums.GuideSection;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.guide.domain.GuideEntry;
import com.batong.guide.dto.GuideDtos.BasicInfo;
import com.batong.guide.dto.GuideDtos.Entry;
import com.batong.guide.dto.GuideDtos.EntryCreateRequest;
import com.batong.guide.dto.GuideDtos.EntryCreateResponse;
import com.batong.guide.dto.GuideDtos.EntryUpdateRequest;
import com.batong.guide.dto.GuideDtos.EntryUpdateResponse;
import com.batong.guide.dto.GuideDtos.GuideResponse;
import com.batong.guide.dto.GuideDtos.Section;
import com.batong.guide.repository.GuideEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** GUIDE-01~04 */
@Service
@RequiredArgsConstructor
public class GuideService {

    private static final int MAX_CONTENT = 500;

    private final GuideEntryRepository entries;
    private final SubjectRoleResolver roles;
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public GuideResponse getGuide(Long subjectId, Long userId) {
        SubjectRole role = roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        List<GuideEntry> all = entries.findBySubjectIdOrderByIdAsc(subjectId);

        List<Section> sections = Arrays.stream(GuideSection.values())
                .map(s -> new Section(s, all.stream().filter(e -> e.getSection() == s)
                        .map(e -> new Entry(e.getId(), e.getContent(), e.getSourceType(), e.getUpdatedAt())).toList()))
                .toList();
        Instant updatedAt = all.stream().map(GuideEntry::getUpdatedAt).max(Comparator.naturalOrder()).orElse(null);

        return new GuideResponse(basicInfo(subjectId), updatedAt, sections, role == SubjectRole.OWNER);
    }

    @Transactional
    public EntryCreateResponse addEntry(Long subjectId, Long userId, EntryCreateRequest request) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        if (request == null || request.section() == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        String content = validContent(request.content());
        // 연타·동시 요청에서 둘 다 "없다"고 보고 저장하지 않도록 대상자 행을 잠가 같은 대상자의 추가를 줄 세운다
        jdbc.sql("SELECT 1 FROM care_subject WHERE subject_id = :s FOR UPDATE").param("s", subjectId)
                .query(Integer.class).single();
        // 같은 섹션에 공백을 뺀 내용이 같은 항목이 있으면 새로 만들지 않고 기존 항목을 돌려준다(ERD guide_entry)
        String key = squash(content);
        return entries.findBySubjectIdAndSection(subjectId, request.section()).stream()
                .filter(e -> squash(e.getContent()).equals(key))
                .findFirst()
                .map(e -> new EntryCreateResponse(e.getId()))
                .orElseGet(() -> new EntryCreateResponse(
                        entries.save(GuideEntry.manual(subjectId, request.section(), content)).getId()));
    }

    @Transactional
    public EntryUpdateResponse updateEntry(Long subjectId, Long entryId, Long userId, EntryUpdateRequest request) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        String content = validContent(request == null ? null : request.content());
        GuideEntry entry = find(subjectId, entryId);
        entry.changeContent(content);
        entries.flush();
        return new EntryUpdateResponse(entry.getId(), entry.getUpdatedAt());
    }

    @Transactional
    public void deleteEntry(Long subjectId, Long entryId, Long userId) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        entries.delete(find(subjectId, entryId));
    }

    private GuideEntry find(Long subjectId, Long entryId) {
        // 다른 대상자의 항목도 같은 404로 숨긴다
        return entries.findByIdAndSubjectId(entryId, subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_ITEM_NOT_FOUND));
    }

    private BasicInfo basicInfo(Long subjectId) {
        return jdbc.sql("SELECT name, birth_year, district, dong, emergency_contact FROM care_subject WHERE subject_id = :s")
                .param("s", subjectId)
                .query((rs, n) -> new BasicInfo(
                        rs.getString("name"),
                        Year.now(ZoneId.of("Asia/Seoul")).getValue() - rs.getInt("birth_year"),
                        District.valueOf(rs.getString("district")),
                        rs.getString("dong"),
                        rs.getString("emergency_contact")))
                .single();
    }

    /** content 1~500자. 앞뒤 공백은 잘라 저장한다. */
    private static String validContent(String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty() || content.length() > MAX_CONTENT || !storable(content)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        return content;
    }

    /** PostgreSQL은 NUL을 저장하지 못하고, 짝 없는 서로게이트는 저장할 때 조용히 '?'로 바뀐다. */
    private static boolean storable(String s) {
        return s.indexOf('\u0000') < 0 && s.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF);
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", "");
    }
}
