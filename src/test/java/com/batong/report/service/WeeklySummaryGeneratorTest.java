package com.batong.report.service;

import com.batong.analysis.prompt.PromptLoader;
import com.batong.analysis.prompt.PromptTemplate;
import com.batong.global.enums.GuideSection;
import com.batong.global.enums.TimeSlot;
import com.batong.infra.llm.LlmClient;
import com.batong.infra.llm.LlmException;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmResponse;
import com.batong.infra.llm.LlmTask;
import com.batong.report.dto.ReportDtos.DailyTrend;
import com.batong.report.dto.ReportDtos.KeyChange;
import com.batong.report.dto.ReportDtos.RepeatedBehavior;
import com.batong.report.dto.ReportDtos.TimePatternCount;
import com.batong.report.dto.ReportDtos.WeeklyReportResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI-05: 3건 미만이면 호출 안 함, 입력은 집계값만(이름 마스킹), 검증에 걸리면 요약 전체 폐기, 실패하면 빈 목록 */
class WeeklySummaryGeneratorTest {

    private static final String SYSTEM = "system";

    LlmClient llm;
    WeeklySummaryGenerator generator;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        PromptLoader loader = mock(PromptLoader.class);
        when(loader.load("summarize_weekly", "v1"))
            .thenReturn(new PromptTemplate("summarize_weekly", "v1", SYSTEM, "[집계]\n{statsJson}"));
        generator = new WeeklySummaryGenerator(llm, loader, new ObjectMapper());
    }

    private static WeeklyReportResponse stats(int recordCount) {
        return new WeeklyReportResponse(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
            recordCount, 7, List.of(),
            List.of(new DailyTrend(LocalDate.of(2026, 10, 28), 1, 2)),
            List.of(new KeyChange(503L, LocalDate.of(2026, 10, 31), GuideSection.PREFERENCE, "민지는 담요를 덮어주면 안정됨")),
            List.of(new RepeatedBehavior("소리 지름", 3)),
            List.of(new TimePatternCount(TimeSlot.AFTERNOON, 3)));
    }

    private void llmReturns(String content) {
        when(llm.complete(any())).thenReturn(new LlmResponse(content, "test", 1));
    }

    @Test
    void 일기_수가_3건_미만이면_LLM을_부르지_않는다() {
        assertThat(generator.summarize(stats(2), "민지")).isEmpty();
        verify(llm, never()).complete(any());
    }

    @Test
    void 입력에는_집계값만_들어가고_대상자_이름은_가려진다() {
        llmReturns("{\"summaryLines\":[]}");

        generator.summarize(stats(5), "민지");

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).complete(captor.capture());
        LlmRequest request = captor.getValue();
        assertThat(request.task()).isEqualTo(LlmTask.SUMMARIZE_WEEKLY);
        assertThat(request.userPrompt()).contains("\"recordCount\":5", "\"behaviorCount\":7", "소리 지름", "AFTERNOON");
        assertThat(request.userPrompt()).contains("대상자는 담요를 덮어주면 안정됨").doesNotContain("민지");
    }

    @Test
    void 입력에_있는_숫자만_쓴_요약은_통과한다() {
        llmReturns("{\"summaryLines\":[\"최근 7일 동안 소리를 지르는 행동이 3번 기록됐어요.\",\"주로 오후에 있었어요.\"]}");

        assertThat(generator.summarize(stats(5), "민지")).containsExactly(
            "최근 7일 동안 소리를 지르는 행동이 3번 기록됐어요.", "주로 오후에 있었어요.");
    }

    @Test
    void 입력에_없는_숫자가_있으면_요약_전체를_버린다() {
        llmReturns("{\"summaryLines\":[\"소리를 지르는 행동이 3번 기록됐어요.\",\"밤에 9번 있었어요.\"]}");

        assertThat(generator.summarize(stats(5), "민지")).isEmpty();
    }

    @Test
    void 금지_표현이_있으면_요약_전체를_버린다() {
        for (String word : List.of("때문", "원인", "진단", "치료")) {
            llmReturns("{\"summaryLines\":[\"소리 " + word + "에 3번 기록됐어요.\"]}");
            assertThat(generator.summarize(stats(5), "민지")).as(word).isEmpty();
        }
    }

    @Test
    void JSON이_아니거나_형식이_다르면_빈_목록이다() {
        for (String bad : List.of("not json", "{}", "{\"summaryLines\":\"문장\"}", "{\"summaryLines\":[1]}",
            "{\"summaryLines\":[\" \"]}")) {
            llmReturns(bad);
            assertThat(generator.summarize(stats(5), "민지")).as(bad).isEmpty();
        }
    }

    @Test
    void LLM_호출이_실패하면_예외를_던지지_않고_빈_목록이다() {
        when(llm.complete(any())).thenThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout"));

        assertThat(generator.summarize(stats(5), "민지")).isEmpty();
    }
}
