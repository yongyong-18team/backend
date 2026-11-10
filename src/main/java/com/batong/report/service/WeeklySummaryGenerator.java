package com.batong.report.service;

import com.batong.analysis.prompt.PromptLoader;
import com.batong.analysis.prompt.PromptTemplate;
import com.batong.analysis.service.DiaryMasker;
import com.batong.infra.llm.LlmClient;
import com.batong.infra.llm.LlmException;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmResponse;
import com.batong.infra.llm.LlmTask;
import com.batong.report.dto.ReportDtos.WeeklyReportResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-05 주간 리포트 요약 문장 (AI 명세 4-3). 서버가 집계한 값만 LLM에 보내고, 문장만 받는다.
 * 일기 원문·근거 문장·보호자 상태는 보내지 않는다. 실패하거나 검증을 통과하지 못하면 빈 목록이고,
 * 화면은 집계 수치만 보여 준다. 호출은 한 번이며 재시도하지 않는다. 요청·응답 본문은 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WeeklySummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryGenerator.class);

    static final String PROMPT_NAME = "summarize_weekly";
    static final String PROMPT_VERSION = "v1";
    /** recordCount가 이보다 적으면 호출하지 않는다(AI 명세 4-3, API 명세 REPORT-03) */
    static final int MIN_RECORD_COUNT = 3;
    /** 기간이 7일이라는 사실은 입력에 숫자로 없어도 "최근 7일"처럼 쓸 수 있게 허용한다 */
    static final int PERIOD_DAYS = 7;
    /** AI 명세 4-3 검증: 이 표현이 있으면 요약 전체를 버린다 */
    static final List<String> FORBIDDEN = List.of("때문", "원인", "진단", "치료");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    /** 입력 JSON의 필드는 AI 명세 4-3 입력 예시와 같다 */
    record SummaryInput(String periodStart, String periodEnd, int recordCount, int behaviorCount,
                        List<DayInput> dailyTrend, List<BehaviorInput> repeatedBehaviors,
                        List<TimeInput> timePattern, List<ChangeInput> keyChanges) {
    }

    record DayInput(String date, int behaviorCount) {
    }

    record BehaviorInput(String label, int count) {
    }

    record TimeInput(String timeSlot, int count) {
    }

    record ChangeInput(String section, String content) {
    }

    /**
     * @param subjectName keyChanges의 content에 이름이 들어 있을 수 있어 "대상자"로 바꾸고 전화번호를 지운다
     * @return 요약 문장. 호출하지 않았거나 실패했거나 검증에 걸리면 빈 목록
     */
    public List<String> summarize(WeeklyReportResponse stats, String subjectName) {
        if (stats.recordCount() < MIN_RECORD_COUNT) {
            return List.of();
        }
        try {
            String statsJson = objectMapper.writeValueAsString(toInput(stats, subjectName));
            PromptTemplate template = promptLoader.load(PROMPT_NAME, PROMPT_VERSION);
            String user = template.renderUser(Map.of("statsJson", statsJson));
            LlmResponse response = llmClient.complete(
                LlmRequest.of(LlmTask.SUMMARIZE_WEEKLY, template.version(), template.system(), user, null));
            return validate(response.content(), statsJson);
        } catch (LlmException e) {
            log.warn("AI-05 호출 실패 type={}", e.getType());
            return List.of();
        } catch (JsonProcessingException e) {
            log.warn("AI-05 입력 직렬화 실패");
            return List.of();
        }
    }

    private SummaryInput toInput(WeeklyReportResponse stats, String subjectName) {
        return new SummaryInput(
            stats.periodStart().toString(), stats.periodEnd().toString(),
            stats.recordCount(), stats.behaviorCount(),
            stats.dailyTrend().stream().map(d -> new DayInput(d.date().toString(), d.behaviorCount())).toList(),
            stats.repeatedBehaviors().stream().map(r -> new BehaviorInput(r.label(), r.count())).toList(),
            stats.timePattern().stream().map(t -> new TimeInput(t.timeSlot().name(), t.count())).toList(),
            stats.keyChanges().stream()
                .map(k -> new ChangeInput(k.section().name(), mask(k.content(), subjectName)))
                .toList());
    }

    private static String mask(String content, String subjectName) {
        return subjectName == null || subjectName.isBlank() ? content : DiaryMasker.mask(content, subjectName);
    }

    /**
     * AI 명세 4-3 검증. JSON이 아니거나 형식이 다르거나, 입력에 없는 숫자가 있거나, 금지 표현이 있으면 요약 전체를 버린다.
     * 숫자는 입력 JSON에 나온 숫자(날짜의 월·일 포함)와 기간 7만 허용한다. "03"과 "3"은 같은 숫자로 본다
     */
    List<String> validate(String content, String statsJson) {
        if (content == null) {
            return List.of();
        }
        JsonNode lines;
        try {
            lines = objectMapper.readTree(content).path("summaryLines");
        } catch (JsonProcessingException e) {
            return List.of();
        }
        if (!lines.isArray()) {
            return List.of();
        }

        Set<BigInteger> allowed = numbers(statsJson);
        allowed.add(BigInteger.valueOf(PERIOD_DAYS));

        List<String> result = new ArrayList<>();
        for (JsonNode node : lines) {
            if (!node.isTextual() || node.asText().isBlank()) {
                return List.of();
            }
            String line = node.asText().strip();
            if (FORBIDDEN.stream().anyMatch(line::contains) || !allowed.containsAll(numbers(line))) {
                return List.of();
            }
            result.add(line);
        }
        return List.copyOf(result);
    }

    private static Set<BigInteger> numbers(String text) {
        Set<BigInteger> numbers = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            numbers.add(new BigInteger(m.group()));
        }
        return numbers;
    }
}
