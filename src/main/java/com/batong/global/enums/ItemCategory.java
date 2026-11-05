package com.batong.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** extracted_item.category. 섹션은 저장하지 않고 이 매핑으로 계산한다(ERD 3-2, AI 명세 3-3). */
@Getter
@RequiredArgsConstructor
public enum ItemCategory {
    COMMUNICATION(GuideSection.COMMUNICATION),
    BEHAVIOR(GuideSection.BEHAVIOR_RESPONSE),
    TRIGGER(GuideSection.BEHAVIOR_RESPONSE),
    RESPONSE(GuideSection.BEHAVIOR_RESPONSE),
    ROUTINE(GuideSection.DAILY_LIFE),
    CONDITION(GuideSection.DAILY_LIFE),
    MEDICATION(GuideSection.MEDICATION),
    MEAL(GuideSection.MEDICATION),
    PREFERENCE(GuideSection.PREFERENCE),
    AVERSION(GuideSection.PREFERENCE);

    private final GuideSection section;
}
