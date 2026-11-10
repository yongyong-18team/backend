package com.batong.infra.llm;

import com.batong.global.enums.BehaviorLabel;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.SafetyLevel;
import com.batong.global.enums.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    private static final String E01_TEXT =
            "오후에 옆 건물 공사 소리가 나니까 민지가 귀를 막더니 30분 넘게 소리를 질렀다. 차에 태워서 트로트 틀어주니까 조용해졌다.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
    private final MockLlmClient client = new MockLlmClient(objectMapper, resourceLoader);

    @Test
    void 평가셋_문장이면_저장된_응답을_돌려준다() throws Exception {
        LlmResponse response = client.complete(analyzeRequest("대상자 ...", E01_TEXT));

        JsonNode json = objectMapper.readTree(response.content());
        assertThat(response.modelName()).isEqualTo("mock");
        assertThat(json.path("safetyLevel").asText()).isEqualTo("NORMAL");
        assertThat(json.path("items")).hasSize(3);
        assertThat(json.path("items").get(1).path("label").asText()).isEqualTo("소리 지름");
    }

    @Test
    void 띄어쓰기와_문장부호가_달라도_같은_응답을_찾는다() throws Exception {
        String typed = "오후에 옆 건물 공사소리가 나니까  민지가 귀를 막더니 30분 넘게 소리를 질렀다\n차에 태워서 트로트 틀어주니까 조용해졌다!!";

        JsonNode json = objectMapper.readTree(client.complete(analyzeRequest("x", typed)).content());

        assertThat(json.path("items")).hasSize(3);
    }

    @Test
    void 위기_사례는_CRISIS를_돌려준다() throws Exception {
        String text = "오늘도 밤새 한숨도 못 잤다. 요즘은 다 그만두고 그냥 사라지고 싶다는 생각만 든다.";

        JsonNode json = objectMapper.readTree(client.complete(analyzeRequest("x", text)).content());

        assertThat(json.path("safetyLevel").asText()).isEqualTo("CRISIS");
        assertThat(json.path("empathy").isNull()).isTrue();
        assertThat(json.path("items")).isEmpty();
    }

    @Test
    void 평가셋에_없으면_기본_NORMAL_응답_1건이고_근거는_마스킹된_본문에_있다() throws Exception {
        String masked = "아침에 대상자가 밥을 잘 먹었다. 오후엔 산책을 했다.";
        String userPrompt = "[일기 날짜] 2026-11-04 (수요일)\n[일기]\n" + masked;

        JsonNode json = objectMapper.readTree(client.complete(analyzeRequest(userPrompt, "평가셋에 없는 원문")).content());

        assertThat(json.path("safetyLevel").asText()).isEqualTo("NORMAL");
        assertThat(json.path("items")).hasSize(1);
        String evidence = json.path("items").get(0).path("evidenceQuote").asText();
        assertThat(evidence).isEqualTo("아침에 대상자가 밥을 잘 먹었다");
        assertThat(masked).contains(evidence);
    }

    @Test
    void 본문이_비어_있으면_기본_응답의_items는_비어_있다() throws Exception {
        JsonNode json = objectMapper.readTree(client.complete(analyzeRequest("[일기]\n   ", "??")).content());

        assertThat(json.path("items")).isEmpty();
    }

    @Test
    void mockKey가_없으면_기본_응답() throws Exception {
        LlmRequest request = LlmRequest.of(LlmTask.ANALYZE_DIARY, "v1", "sys", "[일기]\n오늘은 조용했다.", null);

        JsonNode json = objectMapper.readTree(client.complete(request).content());

        assertThat(json.path("items")).hasSize(1);
    }

    @Test
    void P1_작업은_고정_응답() throws Exception {
        LlmRequest request = LlmRequest.of(LlmTask.EXPLAIN_RECOMMENDATION, "v1", "sys", "user", null);

        JsonNode json = objectMapper.readTree(client.complete(request).content());

        assertThat(json.path("reasonText").asText()).endsWith("제도예요.");
    }

    /**
     * Mock 데이터 자체가 근거 문장 규칙(AI 명세 2-4)을 지키는지 확인한다.
     * 평가셋에 사례를 추가할 때 evidenceQuote를 잘못 적으면 여기서 걸린다.
     */
    @Test
    void 모든_Mock_응답의_근거_문장은_마스킹된_원문에_들어_있다() throws Exception {
        Pattern strip = Pattern.compile("[\\s.,!?~…'\"“”‘’]");
        for (JsonNode evalCase : readEvalSet()) {
            var resource = resourceLoader.getResource(
                    "classpath:ai-eval/mock/" + evalCase.path("id").asText() + ".json");
            if (!resource.exists()) {
                continue;
            }
            String masked = evalCase.path("text").asText().replace(evalCase.path("name").asText(), "대상자");
            String normalizedMasked = strip.matcher(masked).replaceAll("");
            JsonNode response = objectMapper.readTree(resource.getInputStream());
            for (JsonNode item : response.path("items")) {
                String quote = strip.matcher(item.path("evidenceQuote").asText()).replaceAll("");
                assertThat(normalizedMasked)
                        .as("%s의 근거 문장 '%s'", evalCase.path("id").asText(), item.path("evidenceQuote").asText())
                        .contains(quote);
            }
        }
    }

    /** Mock 응답이 AI 명세 3-3 출력 스키마를 지키는지 확인한다. 실제 LLM 응답과 같은 검증을 통과해야 시연 흐름이 같다. */
    @Test
    void 모든_Mock_응답은_출력_스키마를_지킨다() throws Exception {
        for (JsonNode evalCase : readEvalSet()) {
            String id = evalCase.path("id").asText();
            var resource = resourceLoader.getResource("classpath:ai-eval/mock/" + id + ".json");
            if (!resource.exists()) {
                continue;
            }
            JsonNode response = objectMapper.readTree(resource.getInputStream());
            SafetyLevel level = SafetyLevel.valueOf(response.path("safetyLevel").asText());
            JsonNode items = response.path("items");

            assertThat(response.path("burnoutSignal").isBoolean()).as("%s burnoutSignal", id).isTrue();
            assertThat(items.isArray() && items.size() <= 15).as("%s items", id).isTrue();
            if (level == SafetyLevel.CRISIS) {
                assertThat(response.path("empathy").isNull()).as("%s CRISIS면 empathy null", id).isTrue();
                assertThat(items).as("%s CRISIS면 items 비움", id).isEmpty();
                continue;
            }
            assertThat(response.path("empathy").asText()).as("%s empathy", id).isNotBlank().hasSizeLessThanOrEqualTo(60);

            for (JsonNode item : items) {
                ItemCategory category = ItemCategory.valueOf(item.path("category").asText());
                JsonNode label = item.path("label");
                if (category == ItemCategory.BEHAVIOR) {
                    assertThat(BehaviorLabel.fromText(label.asText())).as("%s label", id).isPresent();
                } else {
                    assertThat(label.isNull()).as("%s BEHAVIOR가 아니면 label null", id).isTrue();
                }
                assertThat(item.path("content").asText()).as("%s content", id).isNotBlank().hasSizeLessThanOrEqualTo(80);
                if (!item.path("timeSlot").isNull()) {
                    TimeSlot.valueOf(item.path("timeSlot").asText());
                }
                if (!item.path("durationMin").isNull()) {
                    assertThat(item.path("durationMin").asInt()).as("%s durationMin", id).isBetween(1, 600);
                }
                if (!item.path("episodeNo").isNull()) {
                    assertThat(item.path("episodeNo").asInt()).as("%s episodeNo", id).isPositive();
                }
            }
        }
    }

    private LlmRequest analyzeRequest(String userPrompt, String originalText) {
        return LlmRequest.of(LlmTask.ANALYZE_DIARY, "v1", "sys", userPrompt, LlmMockKey.of(originalText));
    }

    private List<JsonNode> readEvalSet() throws Exception {
        List<JsonNode> cases = new ArrayList<>();
        var resource = resourceLoader.getResource(MockLlmClient.EVAL_SET_PATH);
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    cases.add(objectMapper.readTree(line));
                }
            }
        }
        return cases;
    }
}
