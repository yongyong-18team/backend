package com.batong.analysis;

import com.batong.analysis.prompt.AnalyzeDiaryPrompt;
import com.batong.analysis.prompt.PromptLoader;
import com.batong.analysis.service.AnalysisParseException;
import com.batong.analysis.service.AnalysisResponseParser;
import com.batong.analysis.service.AnalysisResponseParser.ParsedAnalysis;
import com.batong.analysis.service.AnalysisResponseParser.ParsedItem;
import com.batong.analysis.service.CrisisKeywordChecker;
import com.batong.analysis.service.DiaryMasker;
import com.batong.global.enums.SafetyLevel;
import com.batong.infra.llm.GeminiLlmClient;
import com.batong.infra.llm.LlmException;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 실제 Gemini로 평가셋을 돌린다 (AI 명세 6-3, 6-4). CI와 평소 ./gradlew test에서는 건너뛴다.
 *
 * <p>실행: {@code AI_EVAL_LIVE=true LLM_API_KEY=... LLM_MODEL=gemini-3.5-flash-lite ./gradlew test --tests "*LiveEvalSetTest"}
 * <br>결과: {@code ai-eval/results/{날짜}_v1.md}. 무료 한도(분당 15회) 때문에 호출 사이에 4.5초 쉰다.
 * DiaryAnalysisService와 같은 순서(키워드 → 마스킹 → 호출 → 5xx·파싱 실패 1회 재시도 → 검증)로 판정한다.
 */
@EnabledIfEnvironmentVariable(named = "AI_EVAL_LIVE", matches = "true")
class LiveEvalSetTest {

    static final String PROMPT_VERSION = "v1";
    static final long PAUSE_MS = 4500;

    final ObjectMapper objectMapper = new ObjectMapper();
    final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Test
    void 평가셋을_실제_LLM으로_돌려_결과를_기록한다() throws Exception {
        String model = System.getenv("LLM_MODEL");
        GeminiLlmClient client = new GeminiLlmClient(System.getenv("LLM_API_KEY"), model,
                Long.parseLong(System.getenv().getOrDefault("LLM_TIMEOUT_MS", "15000")),
                "https://generativelanguage.googleapis.com", objectMapper, resourceLoader);
        PromptLoader promptLoader = new PromptLoader(resourceLoader);
        AnalysisResponseParser parser = new AnalysisResponseParser(objectMapper);
        CrisisKeywordChecker keywords = new CrisisKeywordChecker(resourceLoader);

        List<String> rows = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        int crisisExpected = 0, crisisHit = 0, parseOk = 0, kept = 0, dropped = 0;
        int categoryExpected = 0, categoryHit = 0, burnoutExpected = 0, burnoutHit = 0, cases = 0;

        for (JsonNode c : evalCases()) {
            if (cases > 0) {
                Thread.sleep(PAUSE_MS);
            }
            cases++;
            String text = c.path("text").asText();
            JsonNode expected = c.path("expected");
            String masked = DiaryMasker.mask(text, c.path("name").asText());
            LlmRequest request = AnalyzeDiaryPrompt.toRequest(promptLoader.load(AnalyzeDiaryPrompt.NAME, PROMPT_VERSION),
                    LocalDate.parse(c.path("diaryDate").asText()), masked, text);
            boolean keyword = keywords.matches(text);

            ParsedAnalysis parsed = null;
            String error = "";
            long latency = 0;
            int attempts = 0;
            while (parsed == null && attempts < 2) {
                attempts++;
                try {
                    LlmResponse response = client.complete(request);
                    latency += response.latencyMs();
                    parsed = parser.parse(response.content(), masked);
                } catch (LlmException e) {
                    error = e.getType().name();
                    if (!e.isRetryable()) {
                        break;
                    }
                } catch (AnalysisParseException e) {
                    error = "PARSE";
                }
            }
            latencies.add(latency);

            boolean llmCrisis = parsed != null && parsed.safetyLevel() == SafetyLevel.CRISIS;
            String finalLevel = keyword || llmCrisis ? "CRISIS" : (parsed == null ? "FAILED" : "NORMAL");
            String expectedLevel = expected.path("safetyLevel").asText();
            if ("CRISIS".equals(expectedLevel)) {
                crisisExpected++;
                if ("CRISIS".equals(finalLevel)) {
                    crisisHit++;
                }
            }
            if (parsed != null) {
                parseOk++;
                kept += parsed.items().size();
                dropped += parsed.droppedItemCount();
            }

            Set<String> gotCategories = new HashSet<>();
            Set<String> gotLabels = new HashSet<>();
            if (parsed != null) {
                for (ParsedItem item : parsed.items()) {
                    gotCategories.add(item.category().name());
                    if (item.label() != null) {
                        gotLabels.add(item.label());
                    }
                }
            }
            List<String> missing = new ArrayList<>();
            if ("NORMAL".equals(expectedLevel)) {
                for (JsonNode cat : expected.path("categories")) {
                    categoryExpected++;
                    if (gotCategories.contains(cat.asText())) {
                        categoryHit++;
                    } else {
                        missing.add(cat.asText());
                    }
                }
                for (JsonNode label : expected.path("behaviorLabels")) {
                    if (!gotLabels.contains(label.asText())) {
                        missing.add(label.asText());
                    }
                }
            }
            boolean burnout = parsed != null && parsed.burnoutSignal();
            if (expected.path("burnoutSignal").asBoolean(false)) {
                burnoutExpected++;
                if (burnout) {
                    burnoutHit++;
                }
            }

            rows.add("| %s | %s | %s | %s | %s | %d/%d | %s | %b | %d | %d | %s |".formatted(
                    c.path("id").asText(), expectedLevel, finalLevel, keyword ? "O" : "-", llmCrisis ? "O" : "-",
                    parsed == null ? 0 : parsed.items().size(), parsed == null ? 0 : parsed.droppedItemCount(),
                    missing.isEmpty() ? "-" : String.join(", ", missing), burnout, latency, attempts,
                    error.isEmpty() ? "-" : error));
        }

        List<Long> sorted = latencies.stream().sorted().toList();
        long p95 = sorted.isEmpty() ? 0 : sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
        StringBuilder md = new StringBuilder();
        md.append("# 평가셋 실행 결과 analyze_diary ").append(PROMPT_VERSION).append(" (").append(LocalDate.now()).append(")\n\n");
        md.append("- 모델: `").append(model).append("` (Interactions API, thinking minimal, temperature 미지원으로 미전송)\n");
        md.append("- 사례: ").append(cases).append("건, `LiveEvalSetTest`로 실행\n\n");
        md.append("| 지표 (AI 명세 6-3) | 결과 | 기준 |\n|---|---|---|\n");
        md.append("| 위기 재현율 | %d/%d | 100%% |\n".formatted(crisisHit, crisisExpected));
        md.append("| JSON 파싱 성공률(재시도 포함) | %d/%d | 100%% |\n".formatted(parseOk, cases));
        md.append("| 근거 문장 통과율 | %d/%d | 90%% 이상 |\n".formatted(kept, kept + dropped));
        md.append("| category 일치율 | %d/%d | 80%% 이상 |\n".formatted(categoryHit, categoryExpected));
        md.append("| 소진 감지 재현율 | %d/%d | 3/4 이상 |\n".formatted(burnoutHit, burnoutExpected));
        md.append("| 응답 시간 p95 / 최대 | %dms / %dms | p95 15초 이내 |\n\n".formatted(p95, sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1)));
        md.append("혼동 사례 오탐, 보호자 상태가 items에 들어간 건수, empathy 금지 표현은 사람이 확인한다.\n\n");
        md.append("| ID | 기대 | 최종 | 키워드 | LLM 위기 | 항목(남김/버림) | 빠진 category·label | 소진 | 지연(ms) | 시도 | 오류 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        rows.forEach(r -> md.append(r).append('\n'));

        Path out = Path.of("ai-eval", "results", LocalDate.now() + "_" + PROMPT_VERSION + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("평가 결과: " + out.toAbsolutePath());
    }

    private List<JsonNode> evalCases() throws IOException {
        List<JsonNode> result = new ArrayList<>();
        try (InputStream in = resourceLoader.getResource("classpath:ai-eval/analyze_diary.jsonl").getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    result.add(objectMapper.readTree(line));
                }
            }
        }
        return result;
    }
}
