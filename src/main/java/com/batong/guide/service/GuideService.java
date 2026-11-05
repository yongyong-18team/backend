package com.batong.guide.service;

import com.batong.global.enums.District;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.SourceType;
import com.batong.guide.dto.GuideDtos.BasicInfo;
import com.batong.guide.dto.GuideDtos.Entry;
import com.batong.guide.dto.GuideDtos.EntryCreateRequest;
import com.batong.guide.dto.GuideDtos.EntryCreateResponse;
import com.batong.guide.dto.GuideDtos.EntryUpdateRequest;
import com.batong.guide.dto.GuideDtos.EntryUpdateResponse;
import com.batong.guide.dto.GuideDtos.GuideResponse;
import com.batong.guide.dto.GuideDtos.ReviewRequest;
import com.batong.guide.dto.GuideDtos.ReviewResponse;
import com.batong.guide.dto.GuideDtos.Section;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.batong.global.StubData.at;

/** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #10(GUIDE), #11(ITEM-01) */
@Service
public class GuideService {

    public GuideResponse getGuide(Long subjectId, Long userId) {
        List<Section> sections = Arrays.stream(GuideSection.values())
                .map(s -> new Section(s, s == GuideSection.BEHAVIOR_RESPONSE
                        ? List.of(new Entry(301L, "공사 소음에 불안해함", SourceType.DIARY, at("2026-11-03T21:10:00+09:00")))
                        : List.of()))
                .toList();
        return new GuideResponse(
                new BasicInfo("민지", 25, District.SUJI, "풍덕천동", "어머니 010-0000-0000"),
                at("2026-11-03T21:10:00+09:00"), sections, true);
    }

    public EntryCreateResponse addEntry(Long subjectId, Long userId, EntryCreateRequest request) {
        return new EntryCreateResponse(310L);
    }

    public EntryUpdateResponse updateEntry(Long subjectId, Long entryId, Long userId, EntryUpdateRequest request) {
        return new EntryUpdateResponse(entryId, at("2026-11-03T22:00:00+09:00"));
    }

    public void deleteEntry(Long subjectId, Long entryId, Long userId) {
    }

    public ReviewResponse review(Long subjectId, Long diaryId, Long userId, ReviewRequest request) {
        return new ReviewResponse(2, 1, 0, List.of(301L, 302L));
    }
}
