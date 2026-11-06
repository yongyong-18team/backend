package com.batong.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/** extracted_item.label. DB·API에는 한글 text를 그대로 저장·응답한다(ERD 3-2). BEHAVIOR 항목에만 쓴다. */
@Getter
@RequiredArgsConstructor
public enum BehaviorLabel {
    SCREAMING("소리 지름"),
    SELF_HARM("자해 행동"),
    AGGRESSION("타인 공격"),
    PROPERTY_DAMAGE("물건 파손"),
    ELOPEMENT("뛰쳐나감"),
    REPETITIVE("반복 행동"),
    SLEEP_PROBLEM("수면 문제"),
    MEAL_REFUSAL("식사 거부"),
    OTHER("기타 행동");

    private final String text;

    public static Optional<BehaviorLabel> fromText(String text) {
        return Arrays.stream(values()).filter(l -> l.text.equals(text)).findFirst();
    }
}
