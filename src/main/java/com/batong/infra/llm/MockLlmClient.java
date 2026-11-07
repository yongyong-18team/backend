package com.batong.infra.llm;

import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.SafetyLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * AI_MODE=mock(기본값)일 때 쓰는 LlmClient (AI 명세 8장).
 *
 * <ul>
 *   <li>AI-01: {@link LlmRequest#mockKey()}로 평가셋 사례를 찾아 {@code ai-eval/mock/{id}.json}을 그대로 돌려준다.
 *       없으면 기본 NORMAL 응답(items 1개).</li>
 *   <li>AI-03~05: 고정 응답.</li>
 * </ul>
 *
 * <p>실제 LLM과 똑같이 JSON 문자열만 돌려주므로, 파싱·검증·저장 코드는 mock/live 구분 없이 같은 길을 탄다.
 */
@Component
@ConditionalOnProperty(name = "app.ai-mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    static final String MODEL_NAME = "mock";
    static final String EVAL_SET_PATH = "classpath:ai-eval/analyze_diary.jsonl";
    static final String MOCK_RESPONSE_PATH = "classpath:ai-eval/mock/%s.json";
    static final String DIARY_MARKER = "[일기]";
    static final String DEFAULT_EMPATHY = "오늘 하루도 기록해 주셔서 고마워요.";
    static final int DEFAULT_EVIDENCE_MAX_LENGTH = 30;

    private final ObjectMapper objectMapper;
    /** mockKey(원문 정규화 해시) → 저장된 응답 JSON */
    private final Map<String, String> responsesByKey;

    public MockLlmClient(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.responsesByKey = loadCases(resourceLoader);
        log.info("MockLlmClient 사용. AI-01 평가셋 Mock 응답 {}건", responsesByKey.size());
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.nanoTime();
        String content = switch (request.task()) {
            case ANALYZE_DIARY -> analyzeDiary(request);
            case SUMMARIZE_SHARE, SUMMARIZE_WEEKLY -> "{\"summaryLines\":[]}";
            case EXPLAIN_RECOMMENDATION -> "{\"reasonText\":\"잠시 돌봄을 맡길 곳이 필요할 때 알아볼 수 있는 제도예요.\"}";
        };
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new LlmResponse(content, MODEL_NAME, latencyMs);
    }

    private String analyzeDiary(LlmRequest request) {
        if (request.mockKey() != null) {
            String stored = responsesByKey.get(request.mockKey());
            if (stored != null) {
                return stored;
            }
        }
        return defaultNormalResponse(extractMaskedDiary(request.userPrompt()));
    }

    /**
     * 평가셋에 없는 일기용 기본 응답.
     * 근거 문장 검증(AI 명세 2-4)을 통과하도록 마스킹된 일기의 첫 문장 일부를 evidenceQuote로 쓴다.
     */
    String defaultNormalResponse(String maskedDiary) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("safetyLevel", SafetyLevel.NORMAL.name());
        root.put("empathy", DEFAULT_EMPATHY);
        ArrayNode items = root.putArray("items");

        String evidence = firstSentence(maskedDiary, DEFAULT_EVIDENCE_MAX_LENGTH);
        if (!evidence.isEmpty()) {
            ObjectNode item = items.addObject();
            item.put("category", ItemCategory.ROUTINE.name());
            item.putNull("label");
            item.put("content", evidence);
            item.putNull("timeSlot");
            item.putNull("durationMin");
            item.putNull("episodeNo");
            item.put("evidenceQuote", evidence);
        }
        root.put("burnoutSignal", false);
        return root.toString();
    }

    /** 사용자 프롬프트(analyze_diary_v1 User 부분)에서 "[일기]" 아래 본문만 꺼낸다. */
    static String extractMaskedDiary(String userPrompt) {
        int idx = userPrompt.indexOf(DIARY_MARKER);
        String body = idx < 0 ? userPrompt : userPrompt.substring(idx + DIARY_MARKER.length());
        return body.strip();
    }

    static String firstSentence(String text, int maxLength) {
        String trimmed = text.strip();
        int end = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                end = i;
                break;
            }
        }
        String sentence = trimmed.substring(0, end).strip();
        return sentence.length() <= maxLength ? sentence : sentence.substring(0, maxLength).strip();
    }

    private Map<String, String> loadCases(ResourceLoader resourceLoader) {
        Map<String, String> result = new HashMap<>();
        Resource evalSet = resourceLoader.getResource(EVAL_SET_PATH);
        if (!evalSet.exists()) {
            log.warn("평가셋 파일이 없어 모든 AI-01 요청에 기본 응답을 씁니다: {}", EVAL_SET_PATH);
            return result;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(evalSet.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode evalCase = objectMapper.readTree(line);
                String id = evalCase.path("id").asText(null);
                String text = evalCase.path("text").asText(null);
                if (id == null || text == null) {
                    log.warn("평가셋 {}번째 줄에 id 또는 text가 없어 건너뜁니다", lineNo);
                    continue;
                }
                String response = loadMockResponse(resourceLoader, id);
                if (response != null) {
                    result.put(LlmMockKey.of(text), response);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("평가셋을 읽지 못했습니다: " + EVAL_SET_PATH, e);
        }
        return result;
    }

    /** 응답 파일이 없는 사례(아직 기대값만 적은 사례)는 건너뛴다. 파일이 JSON이 아니면 기동을 멈춘다. */
    private String loadMockResponse(ResourceLoader resourceLoader, String id) throws IOException {
        Resource resource = resourceLoader.getResource(MOCK_RESPONSE_PATH.formatted(id));
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode json = objectMapper.readTree(in);
            return objectMapper.writeValueAsString(json);
        }
    }
}
