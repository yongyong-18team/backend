package com.batong.guide.dto;

import com.batong.global.enums.District;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.ItemStatus;
import com.batong.global.enums.SourceType;

import java.time.Instant;
import java.util.List;

/** GUIDE-01~04, ITEM-01 요청·응답 */
public final class GuideDtos {

    private GuideDtos() {
    }

    /** sections는 7개를 GuideSection 순서로 항상 내려준다. editable: OWNER true, CO false */
    public record GuideResponse(BasicInfo basicInfo, Instant updatedAt, List<Section> sections, boolean editable) {
    }

    public record BasicInfo(String name, int age, District district, String dong, String emergencyContact) {
    }

    public record Section(GuideSection section, List<Entry> entries) {
    }

    public record Entry(Long entryId, String content, SourceType sourceType, Instant updatedAt) {
    }

    public record EntryCreateRequest(GuideSection section, String content) {
    }

    public record EntryCreateResponse(Long entryId) {
    }

    public record EntryUpdateRequest(String content) {
    }

    public record EntryUpdateResponse(Long entryId, Instant updatedAt) {
    }

    public record ReviewRequest(List<Decision> decisions) {
    }

    /** content가 있으면 수정 후 승인(edited=true) */
    public record Decision(int itemNo, ItemStatus status, String content) {
    }

    public record ReviewResponse(int approvedCount, int excludedCount, int duplicateSkippedCount,
                                 List<Long> createdGuideEntryIds) {
    }
}
