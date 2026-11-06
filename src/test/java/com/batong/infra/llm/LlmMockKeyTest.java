package com.batong.infra.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmMockKeyTest {

    @Test
    void 공백과_문장부호는_무시한다() {
        assertThat(LlmMockKey.of("민지가 소리를 질렀다."))
                .isEqualTo(LlmMockKey.of("민지가소리를  질렀다!!\n"));
    }

    @Test
    void 글자가_다르면_키가_다르다() {
        assertThat(LlmMockKey.of("민지가 소리를 질렀다"))
                .isNotEqualTo(LlmMockKey.of("민지가 소리를 질렀어"));
    }

    @Test
    void 분리된_한글_자모도_같은_키() {
        String nfd = java.text.Normalizer.normalize("민지", java.text.Normalizer.Form.NFD);
        assertThat(LlmMockKey.of(nfd)).isEqualTo(LlmMockKey.of("민지"));
    }

    @Test
    void null이면_null() {
        assertThat(LlmMockKey.of(null)).isNull();
    }

    @Test
    void 요청_toString에_프롬프트_본문이_없다() {
        LlmRequest request = LlmRequest.of(LlmTask.ANALYZE_DIARY, "v1", "시스템", "일기 본문 비밀", "k");
        assertThat(request.toString()).doesNotContain("일기 본문 비밀").doesNotContain("시스템");
    }
}
