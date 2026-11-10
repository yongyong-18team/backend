package com.batong.analysis.prompt;

import com.batong.infra.llm.LlmMockKey;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmTask;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * AI-01 요청을 만든다. AnalysisService는 마스킹이 끝난 뒤 이 메서드 하나만 부르면 된다.
 */
public final class AnalyzeDiaryPrompt {

    public static final String NAME = "analyze_diary";

    private AnalyzeDiaryPrompt() {
    }

    /**
     * @param template     {@code promptLoader.load(NAME, "v1")}
     * @param diaryDate    일기 날짜 (상대 날짜 계산 기준)
     * @param maskedText   마스킹된 일기 본문. LLM에 가는 것은 이것뿐이다
     * @param originalText 마스킹 전 원문. Mock 키(해시)만 만들고 요청에는 넣지 않는다
     */
    public static LlmRequest toRequest(PromptTemplate template, LocalDate diaryDate,
                                       String maskedText, String originalText) {
        String user = template.renderUser(Map.of(
                "diaryDate", diaryDate.toString(),
                "dayOfWeek", diaryDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN),
                "maskedText", maskedText
        ));
        return LlmRequest.of(LlmTask.ANALYZE_DIARY, template.version(), template.system(), user,
                LlmMockKey.of(originalText));
    }
}
