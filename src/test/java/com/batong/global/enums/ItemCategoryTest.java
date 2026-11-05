package com.batong.global.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCategoryTest {

    @Test
    void 일기에서_오는_섹션은_절대_금지와_건강_위험을_제외한_5개() {
        Set<GuideSection> fromDiary = Arrays.stream(ItemCategory.values())
                .map(ItemCategory::getSection).collect(Collectors.toSet());

        assertThat(fromDiary).isEqualTo(EnumSet.complementOf(EnumSet.of(GuideSection.PROHIBITED, GuideSection.HEALTH_RISK)));
        assertThat(ItemCategory.MEAL.getSection()).isEqualTo(GuideSection.MEDICATION);
        assertThat(ItemCategory.AVERSION.getSection()).isEqualTo(GuideSection.PREFERENCE);
    }

    @Test
    void 행동_라벨은_한글_값으로_찾는다() {
        assertThat(BehaviorLabel.fromText("소리 지름")).contains(BehaviorLabel.SCREAMING);
        assertThat(BehaviorLabel.fromText("없는 라벨")).isEmpty();
    }
}
