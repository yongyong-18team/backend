package com.batong.guide.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.ItemStatus;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.guide.dto.GuideDtos.Decision;
import com.batong.guide.dto.GuideDtos.ReviewRequest;
import com.batong.guide.dto.GuideDtos.ReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ITEM-01. 승인된 항목을 설명서에 반영한다. 이미 승인된 항목은 다시 검토할 수 없고(COMMON_VALIDATION),
 * 승인이 하나도 없으면 GUIDE_NOTHING_TO_APPLY다. 같은 섹션에 공백을 뺀 내용이 같은 항목이 있으면
 * 새로 만들지 않고 duplicateSkippedCount로 센다(approvedCount에는 포함).
 */
@Service
@RequiredArgsConstructor
public class ItemReviewService {

    private static final int MAX_CONTENT = 500;

    private final SubjectRoleResolver roles;
    private final JdbcClient jdbc;

    private record Item(String category, String content, ItemStatus status) { }

    @Transactional
    public ReviewResponse review(Long subjectId, Long diaryId, Long userId, ReviewRequest request) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        List<Decision> decisions = request == null || request.decisions() == null ? List.of() : request.decisions();
        validate(decisions);

        // 같은 대상자의 동시 검토·설명서 추가가 중복 항목을 만들지 않게 대상자 행을 잠근다
        jdbc.sql("SELECT subject_id FROM care_subject WHERE subject_id = :s FOR UPDATE").param("s", subjectId)
                .query(Long.class).optional().orElseThrow(() -> new BusinessException(ErrorCode.GUIDE_ITEM_NOT_FOUND));
        boolean mine = jdbc.sql("SELECT EXISTS (SELECT 1 FROM diary WHERE diary_id = :d AND subject_id = :s)")
                .param("d", diaryId).param("s", subjectId).query(Boolean.class).single();
        if (!mine) {
            throw new BusinessException(ErrorCode.GUIDE_ITEM_NOT_FOUND);
        }
        Map<Integer, Item> items = jdbc.sql("SELECT item_no, category, content, status FROM extracted_item WHERE diary_id = :d FOR UPDATE")
                .param("d", diaryId)
                .query((rs, n) -> Map.entry(rs.getInt("item_no"),
                        new Item(rs.getString("category"), rs.getString("content"), ItemStatus.valueOf(rs.getString("status")))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        int approved = 0;
        int excluded = 0;
        int duplicates = 0;
        List<Long> created = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        for (Decision d : decisions) {
            Item item = items.get(d.itemNo());
            if (item == null) {
                throw new BusinessException(ErrorCode.GUIDE_ITEM_NOT_FOUND);
            }
            if (item.status() == ItemStatus.APPROVED) {
                throw new BusinessException(ErrorCode.COMMON_VALIDATION);
            }
            if (d.status() == ItemStatus.EXCLUDED) {
                jdbc.sql("UPDATE extracted_item SET status = 'EXCLUDED' WHERE diary_id = :d AND item_no = :n")
                        .param("d", diaryId).param("n", d.itemNo()).update();
                excluded++;
                continue;
            }
            boolean edited = d.content() != null;
            String content = edited ? d.content().strip() : item.content();
            jdbc.sql("UPDATE extracted_item SET status = 'APPROVED', content = :c, edited = :e WHERE diary_id = :d AND item_no = :n")
                    .param("c", content).param("e", edited).param("d", diaryId).param("n", d.itemNo()).update();
            approved++;
            GuideSection section = ItemCategory.valueOf(item.category()).getSection();
            String key = squash(content);
            boolean duplicate = jdbc.sql("SELECT content FROM guide_entry WHERE subject_id = :s AND section = :sec")
                    .param("s", subjectId).param("sec", section.name()).query(String.class).list().stream()
                    .anyMatch(c -> squash(c).equals(key));
            if (duplicate) {
                duplicates++;
                continue;
            }
            created.add(jdbc.sql("""
                    INSERT INTO guide_entry (subject_id, section, content, source_type, src_diary_id, src_item_no, created_at, updated_at)
                    VALUES (:s, :sec, :c, 'DIARY', :d, :n, :now, :now) RETURNING entry_id""")
                    .param("s", subjectId).param("sec", section.name()).param("c", content)
                    .param("d", diaryId).param("n", d.itemNo()).param("now", now).query(Long.class).single());
        }
        return new ReviewResponse(approved, excluded, duplicates, created);
    }

    private static void validate(List<Decision> decisions) {
        if (decisions.isEmpty()) {
            throw new BusinessException(ErrorCode.GUIDE_NOTHING_TO_APPLY);
        }
        Set<Integer> seen = new HashSet<>();
        int approved = 0;
        for (Decision d : decisions) {
            if (d == null || !seen.add(d.itemNo())
                    || (d.status() != ItemStatus.APPROVED && d.status() != ItemStatus.EXCLUDED)) {
                throw new BusinessException(ErrorCode.COMMON_VALIDATION);
            }
            if (d.content() != null) {
                String c = d.content().strip();
                if (d.status() != ItemStatus.APPROVED || c.isEmpty() || c.length() > MAX_CONTENT || !storable(c)) {
                    throw new BusinessException(ErrorCode.COMMON_VALIDATION);
                }
            }
            if (d.status() == ItemStatus.APPROVED) {
                approved++;
            }
        }
        if (approved == 0) {
            throw new BusinessException(ErrorCode.GUIDE_NOTHING_TO_APPLY);
        }
    }

    /** NUL은 PostgreSQL이 저장하지 못하고, 짝 없는 서로게이트는 '?'로 바뀐다 */
    private static boolean storable(String s) {
        return s.codePoints().noneMatch(c -> c == 0 || (c >= 0xD800 && c <= 0xDFFF));
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", "");
    }
}
