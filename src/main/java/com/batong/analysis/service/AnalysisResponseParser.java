package com.batong.analysis.service;

import com.batong.global.enums.BehaviorLabel;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.SafetyLevel;
import com.batong.global.enums.TimeSlot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * AI-01 응답 파싱·검증 (AI 명세 2-3, 2-4, 3-3, 3-4).
 *
 * <p>JSON이 아니거나 safetyLevel을 알 수 없으면 {@link AnalysisParseException}(재시도 대상, AI 명세 2-1).
 * 그 밖에 검증을 통과하지 못한 항목은 버리고 건수만 센다. 이름 복원과 저장은 호출하는 쪽이 한다.
 */
@Component
@RequiredArgsConstructor
public class AnalysisResponseParser {

    static final int EMPATHY_MAX = 60;
    static final int CONTENT_MAX = 80;
    static final int EVIDENCE_MAX = 500;
    static final int ITEMS_MAX = 15;
    static final int DURATION_MIN = 1;
    static final int DURATION_MAX = 600;

    /** 근거 문장 비교 전 지우는 것: 공백, 문장부호 .,!?~…'"“”‘’ (AI 명세 2-4) */
    private static final Pattern EVIDENCE_IGNORED = Pattern.compile("[\\s.,!?~…'\"“”‘’]");

    private final ObjectMapper objectMapper;

    /**
     * @param content    LLM이 돌려준 본문
     * @param maskedText LLM에 보낸 마스킹된 일기 본문. 근거 문장은 이것과 비교한다
     */
    public ParsedAnalysis parse(String content, String maskedText) {
        JsonNode root = readTree(content);
        SafetyLevel safetyLevel = safetyLevel(root.path("safetyLevel"));
        boolean burnoutSignal = root.path("burnoutSignal").isBoolean() && root.path("burnoutSignal").booleanValue();

        // CRISIS면 공감 문장·추출 항목을 쓰지 않는다(AI 명세 3-4-3)
        if (safetyLevel == SafetyLevel.CRISIS) {
            return new ParsedAnalysis(SafetyLevel.CRISIS, null, List.of(), burnoutSignal, 0);
        }

        String normalizedMasked = normalizeEvidence(maskedText);
        List<ParsedItem> items = new ArrayList<>();
        int dropped = 0;
        JsonNode itemNodes = root.path("items");
        if (itemNodes.isArray()) {
            for (JsonNode node : itemNodes) {
                Optional<ParsedItem> item = item(node, normalizedMasked);
                if (item.isPresent() && items.size() < ITEMS_MAX) {
                    items.add(item.get());
                } else {
                    dropped++;
                }
            }
        }
        return new ParsedAnalysis(SafetyLevel.NORMAL, empathy(root.path("empathy")), List.copyOf(items),
                burnoutSignal, dropped);
    }

    private JsonNode readTree(String content) {
        if (content == null) {
            throw new AnalysisParseException("응답 본문 없음");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw new AnalysisParseException("JSON 객체가 아님");
            }
            return root;
        } catch (JsonProcessingException e) {
            // 메시지에 응답 본문이 섞이지 않도록 원인 예외를 붙이지 않는다(AI 명세 2-6)
            throw new AnalysisParseException("JSON 파싱 실패");
        }
    }

    private static SafetyLevel safetyLevel(JsonNode node) {
        return enumOf(SafetyLevel.class, node)
                .orElseThrow(() -> new AnalysisParseException("safetyLevel 없음 또는 알 수 없음"));
    }

    /** 60자를 넘거나 비어 있으면 자르지 않고 버린다(AI 명세 2-3-3) */
    private static String empathy(JsonNode node) {
        if (!node.isTextual() || node.textValue().isBlank() || length(node.textValue()) > EMPATHY_MAX) {
            return null;
        }
        return node.textValue();
    }

    private static Optional<ParsedItem> item(JsonNode node, String normalizedMasked) {
        Optional<ItemCategory> category = enumOf(ItemCategory.class, node.path("category"));
        if (category.isEmpty()) {
            return Optional.empty();
        }
        JsonNode timeSlotNode = node.path("timeSlot");
        TimeSlot timeSlot = null;
        if (!timeSlotNode.isMissingNode() && !timeSlotNode.isNull()) {
            Optional<TimeSlot> parsed = enumOf(TimeSlot.class, timeSlotNode);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            timeSlot = parsed.get();
        }
        String content = text(node.path("content"), CONTENT_MAX);
        String evidence = text(node.path("evidenceQuote"), EVIDENCE_MAX);
        if (content == null || evidence == null) {
            return Optional.empty();
        }
        String normalizedEvidence = normalizeEvidence(evidence);
        if (normalizedEvidence.isEmpty() || !normalizedMasked.contains(normalizedEvidence)) {
            return Optional.empty();
        }
        return Optional.of(new ParsedItem(category.get(), label(category.get(), node.path("label")), content,
                timeSlot, intInRange(node.path("durationMin"), DURATION_MIN, DURATION_MAX),
                intInRange(node.path("episodeNo"), 1, Short.MAX_VALUE), evidence));
    }

    /** BEHAVIOR가 아니면 null, 목록에 없는 label은 "기타 행동"(AI 명세 3-4-7) */
    private static String label(ItemCategory category, JsonNode node) {
        if (category != ItemCategory.BEHAVIOR) {
            return null;
        }
        return BehaviorLabel.fromText(node.isTextual() ? node.textValue() : null)
                .orElse(BehaviorLabel.OTHER)
                .getText();
    }

    /** 문자열이 아니거나, 비어 있거나, 길이를 넘으면 null */
    private static String text(JsonNode node, int max) {
        if (!node.isTextual() || node.textValue().isBlank() || length(node.textValue()) > max) {
            return null;
        }
        return node.textValue();
    }

    /** 정수가 아니거나 범위 밖이면 값만 버린다(null). 항목은 남긴다 */
    private static Integer intInRange(JsonNode node, int min, int max) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        int value = node.intValue();
        return value >= min && value <= max ? value : null;
    }

    private static <E extends Enum<E>> Optional<E> enumOf(Class<E> type, JsonNode node) {
        if (!node.isTextual()) {
            return Optional.empty();
        }
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(node.textValue())).findFirst();
    }

    static String normalizeEvidence(String text) {
        return EVIDENCE_IGNORED.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll("");
    }

    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    /** CRISIS면 empathy null, items 비어 있음. droppedItemCount는 로그용(AI 명세 2-6 "버려진 항목 수") */
    public record ParsedAnalysis(SafetyLevel safetyLevel, String empathy, List<ParsedItem> items,
                                 boolean burnoutSignal, int droppedItemCount) {
    }

    /** label은 BEHAVIOR만 BehaviorLabel의 한글 text. content·evidenceQuote는 아직 마스킹된 상태 */
    public record ParsedItem(ItemCategory category, String label, String content, TimeSlot timeSlot,
                             Integer durationMin, Integer episodeNo, String evidenceQuote) {
    }
}
