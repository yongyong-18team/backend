package com.batong.guide;

import com.batong.global.enums.GuideSection;
import com.batong.global.enums.SourceType;
import com.batong.guide.domain.GuideEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuideEntryTest {

    @Test
    void 온보딩_항목은_출처와_질문_코드를_가진다() {
        GuideEntry e = GuideEntry.onboarding(10L, GuideSection.values()[0], "매운 음식을 못 먹어요.", "Q2");

        assertThat(e.getSourceType()).isEqualTo(SourceType.ONBOARDING);
        assertThat(e.getSrcQuestionCode()).isEqualTo("Q2");
        assertThat(e.getSubjectId()).isEqualTo(10L);
    }
}
