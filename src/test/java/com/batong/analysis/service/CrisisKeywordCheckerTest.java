package com.batong.analysis.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.text.Normalizer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrisisKeywordCheckerTest {

    private final CrisisKeywordChecker checker = new CrisisKeywordChecker(new DefaultResourceLoader());

    @Test
    void 사전_파일을_읽고_주석과_빈_줄은_건너뛴다() {
        assertThat(checker.size()).isEqualTo(5);
    }

    @Test
    void 평가셋_위기_사례_중_사전_표현이_있는_것은_잡는다() {
        // E27 자살 생각, E24 동반 생각
        assertThat(checker.matches("오늘도 밤새 한숨도 못 잤다. 요즘은 다 그만두고 그냥 사라지고 싶다는 생각만 든다.")).isTrue();
        assertThat(checker.matches("차라리 민지를 데리고 같이 죽는 게 낫겠다는 생각까지 했다.")).isTrue();
    }

    @Test
    void 띄어쓰기가_달라도_분리된_자모여도_잡는다() {
        assertThat(checker.matches("그냥 사라지 고싶다")).isTrue();
        assertThat(checker.matches(Normalizer.normalize("죽고 싶다", Normalizer.Form.NFD))).isTrue();
    }

    @Test
    void 관용_표현과_대상자의_도전_행동_기록은_잡지_않는다() {
        // E28 관용 표현, E30 대상자 자해, E29 대상자가 남을 때림
        assertThat(checker.matches("하루 종일 뛰어다녔더니 힘들어 죽겠다.")).isFalse();
        assertThat(checker.matches("저녁 먹다가 민지가 갑자기 머리를 벽에 박았다.")).isFalse();
        assertThat(checker.matches("민지가 옆에 있던 아주머니 팔을 때렸다.")).isFalse();
    }

    @Test
    void 사전에_없는_위기_표현은_LLM이_잡아야_한다() {
        // E25 타해 생각, E26 즉시 위험: 키워드로는 못 잡아 LLM safetyLevel에 맡긴다(위기 이중 판정, TRD 8-4)
        assertThat(checker.matches("민지를 때려서라도 조용히 시키고 싶다는 생각이 들어서 내가 무서웠다.")).isFalse();
        assertThat(checker.matches("방금 민지가 계단에서 굴러서 머리에서 피가 많이 나는데 불러도 대답이 없다.")).isFalse();
    }

    @Test
    void 사전이_비어_있으면_기동을_멈춘다() {
        assertThatThrownBy(() -> new CrisisKeywordChecker(List.of("# 주석만", "  ")))
                .isInstanceOf(IllegalStateException.class);
    }
}
