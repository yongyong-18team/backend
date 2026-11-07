package com.batong.analysis.prompt;

import com.batong.infra.llm.LlmMockKey;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmTask;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.LocalDate;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyzeDiaryPromptTest {

    private final PromptLoader loader = new PromptLoader(new DefaultResourceLoader());

    @Test
    void v1_프롬프트를_읽어_SYSTEM과_USER로_나눈다() {
        PromptTemplate template = loader.load(AnalyzeDiaryPrompt.NAME, "v1");

        assertThat(template.system()).startsWith("너는 발달장애인 돌봄 기록을 정리하는 도우미다.");
        assertThat(template.system()).endsWith("설명 문장을 붙이지 않는다.");
        assertThat(template.system()).doesNotContain("### USER");
        assertThat(template.user()).startsWith("[일기 날짜] {diaryDate} ({dayOfWeek})");
    }

    @Test
    void 요청을_만든다() {
        PromptTemplate template = loader.load(AnalyzeDiaryPrompt.NAME, "v1");
        String original = "민지가 소리를 질렀다.";

        LlmRequest request = AnalyzeDiaryPrompt.toRequest(
                template, LocalDate.of(2026, 11, 3), "대상자가 소리를 질렀다.", original);

        assertThat(request.userPrompt()).isEqualTo("[일기 날짜] 2026-11-03 (화요일)\n[일기]\n대상자가 소리를 질렀다.");
        assertThat(request.userPrompt()).doesNotContain("민지");
        assertThat(request.task()).isEqualTo(LlmTask.ANALYZE_DIARY);
        assertThat(request.promptVersion()).isEqualTo("v1");
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.maxOutputTokens()).isEqualTo(1500);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(request.mockKey()).isEqualTo(LlmMockKey.of(original));
    }

    @Test
    void 일기_본문의_중괄호_글자는_치환하지_않는다() {
        PromptTemplate template = loader.load(AnalyzeDiaryPrompt.NAME, "v1");

        LlmRequest request = AnalyzeDiaryPrompt.toRequest(
                template, LocalDate.of(2026, 11, 3), "오늘 {diaryDate} $1 그대로", "x");

        assertThat(request.userPrompt()).endsWith("오늘 {diaryDate} $1 그대로");
    }

    @Test
    void 형식이_틀린_프롬프트_파일은_거부한다() {
        assertThatThrownBy(() -> PromptTemplate.parse("bad", "v1", "그냥 텍스트"))
                .isInstanceOf(IllegalStateException.class);
    }
}
