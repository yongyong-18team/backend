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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    private static final String E01_TEXT =
            "출근길에 버스가 평소보다 늦게 왔다. 민지가 정류장에서 계속 귀를 막고 소리를 질렀다. 버스가 오고 자리에 앉으니까 조금 조용해졌다.";

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
        String typed = "출근길에 버스가 평소보다  늦게 왔다\n민지가 정류장에서 계속 귀를막고 소리를 질렀다. 버스가 오고 자리에 앉으니까 조금 조용해졌다!!";

        JsonNode json = objectMapper.readTree(client.complete(analyzeRequest("x", typed)).content());

        assertThat(json.path("items")).hasSize(3);
    }

    @Test
    void 위기_사례는_CRISIS를_돌려준다() throws Exception {
        String text = "오늘 민지와 다투는 동안 화가 너무 났다. 내가 민지를 다치게 할 것 같다는 생각이 들어서 겁이 났다.";

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

    /** 기획이 문장을 바꾸면 Mock이 기본 NORMAL로 떨어진다. 위기 사례가 조용히 NORMAL이 되는 것을 막는다 */
    @Test
    void 평가셋의_모든_사례에_Mock_응답이_있다() throws Exception {
        List<JsonNode> cases = readEvalSet();
        assertThat(cases).hasSize(30);
        for (JsonNode evalCase : cases) {
            String id = evalCase.path("id").asText();
            assertThat(resourceLoader.getResource("classpath:ai-eval/mock/" + id + ".json").exists())
                    .as("%s Mock 파일", id).isTrue();
            JsonNode json = objectMapper.readTree(
                    client.complete(analyzeRequest("x", evalCase.path("text").asText())).content());
            assertThat(json.path("safetyLevel").asText())
                    .as("%s 원문 해시로 찾은 응답", id).isEqualTo(evalCase.path("expected").path("safetyLevel").asText());
        }
    }

    /** Mock 응답은 평가셋 기대값과 같아야 한다. 시연에서 보이는 결과가 곧 기대 판정이다 */
    @Test
    void Mock_응답은_평가셋_기대값과_같다() throws Exception {
        for (JsonNode evalCase : readEvalSet()) {
            String id = evalCase.path("id").asText();
            JsonNode expected = evalCase.path("expected");
            JsonNode response = objectMapper.readTree(
                    resourceLoader.getResource("classpath:ai-eval/mock/" + id + ".json").getInputStream());
            assertThat(response.path("safetyLevel").asText()).as("%s safetyLevel", id)
                    .isEqualTo(expected.path("safetyLevel").asText());
            if (!"NORMAL".equals(expected.path("safetyLevel").asText())) {
                continue;
            }
            Set<String> categories = new HashSet<>();
            Set<String> labels = new HashSet<>();
            for (JsonNode item : response.path("items")) {
                categories.add(item.path("category").asText());
                if (!item.path("label").isNull()) {
                    labels.add(item.path("label").asText());
                }
            }
            assertThat(categories).as("%s category", id).isEqualTo(texts(expected.path("categories")));
            assertThat(labels).as("%s label", id).isEqualTo(texts(expected.path("behaviorLabels")));
            assertThat(response.path("burnoutSignal").asBoolean()).as("%s burnoutSignal", id)
                    .isEqualTo(expected.path("burnoutSignal").asBoolean());
        }
    }

    private static Set<String> texts(JsonNode array) {
        Set<String> result = new HashSet<>();
        array.forEach(v -> result.add(v.asText()));
        return result;
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
