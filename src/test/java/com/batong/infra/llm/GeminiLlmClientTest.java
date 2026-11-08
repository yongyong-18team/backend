package com.batong.infra.llm;

import com.batong.global.enums.BehaviorLabel;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.SafetyLevel;
import com.batong.global.enums.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiLlmClientTest {

    static final String KEY = "test-key-not-real";
    static final String MODEL = "gemini-test-model";
    static final String DIARY_LIKE_BODY = "가상 일기 본문 응답";

    final ObjectMapper objectMapper = new ObjectMapper();
    final ResourceLoader resourceLoader = new DefaultResourceLoader();

    HttpServer server;
    final AtomicReference<String> receivedBody = new AtomicReference<>();
    final AtomicReference<String> receivedKey = new AtomicReference<>();
    final AtomicReference<String> receivedPath = new AtomicReference<>();
    volatile int status = 200;
    volatile String responseBody = "{}";
    volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException ignored) {
                // 클라이언트가 타임아웃으로 먼저 끊은 경우
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    GeminiLlmClient client(long timeoutMs) {
        return new GeminiLlmClient(KEY, MODEL, timeoutMs, "http://127.0.0.1:" + server.getAddress().getPort(),
                objectMapper, resourceLoader);
    }

    static LlmRequest analyzeRequest() {
        return LlmRequest.of(LlmTask.ANALYZE_DIARY, "v1", "시스템 프롬프트", "[일기]\n대상자가 웃었다.", "hash");
    }

    static String interaction(String text) {
        return """
                {"id":"i-1","status":"completed",
                 "steps":[
                   {"type":"thought","signature":"sig","summary":[{"type":"text","text":"생각 요약"}]},
                   {"type":"model_output","content":[{"type":"text","text":%s}]}
                 ],
                 "usage":{"total_input_tokens":100,"total_output_tokens":50,"total_thought_tokens":20,"total_tokens":170}}
                """.formatted(quote(text));
    }

    static String quote(String s) {
        try {
            return new ObjectMapper().writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 요청은_키_헤더_모델_스키마_store_false로_간다() throws Exception {
        responseBody = interaction("{}");

        client(15000).complete(analyzeRequest());

        assertThat(receivedPath.get()).isEqualTo("/v1beta/interactions");
        assertThat(receivedKey.get()).isEqualTo(KEY);
        JsonNode body = objectMapper.readTree(receivedBody.get());
        assertThat(body.path("model").asText()).isEqualTo(MODEL);
        assertThat(body.path("system_instruction").asText()).isEqualTo("시스템 프롬프트");
        assertThat(body.path("input").asText()).isEqualTo("[일기]\n대상자가 웃었다.");
        assertThat(body.path("store").asBoolean(true)).isFalse();
        assertThat(body.path("generation_config").path("max_output_tokens").asInt()).isEqualTo(1500);
        assertThat(body.path("generation_config").path("thinking_level").asText()).isEqualTo("minimal");
        assertThat(body.path("generation_config").has("temperature")).isFalse();
        assertThat(body.path("response_format").path("mime_type").asText()).isEqualTo("application/json");
        assertThat(body.path("response_format").path("schema").path("properties").has("safetyLevel")).isTrue();
        // Mock 전용 키(원문 해시)는 공급자에게 보내지 않는다
        assertThat(receivedBody.get()).doesNotContain("hash");
    }

    @Test
    void 응답에서_model_output_텍스트만_꺼낸다() {
        responseBody = interaction("{\"safetyLevel\":\"NORMAL\"}");

        LlmResponse response = client(15000).complete(analyzeRequest());

        assertThat(response.content()).isEqualTo("{\"safetyLevel\":\"NORMAL\"}");
        assertThat(response.modelName()).isEqualTo(MODEL);
    }

    @Test
    void 스키마_파일이_없는_작업은_스키마_없이_JSON만_요청한다() throws Exception {
        responseBody = interaction("{}");

        client(15000).complete(LlmRequest.of(LlmTask.SUMMARIZE_SHARE, "v1", "s", "u", null));

        JsonNode format = objectMapper.readTree(receivedBody.get()).path("response_format");
        assertThat(format.path("mime_type").asText()).isEqualTo("application/json");
        assertThat(format.has("schema")).isFalse();
    }

    @Test
    void HTTP_5xx는_재시도_대상이고_메시지에_응답_본문이_없다() {
        status = 503;
        responseBody = DIARY_LIKE_BODY;

        assertThatThrownBy(() -> client(15000).complete(analyzeRequest()))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.getType()).isEqualTo(LlmException.Type.SERVER_ERROR);
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getMessage()).doesNotContain(DIARY_LIKE_BODY).doesNotContain(KEY);
                });
    }

    @Test
    void HTTP_4xx와_한도_초과는_재시도하지_않는다() {
        for (int code : new int[]{400, 403, 429}) {
            status = code;
            assertThatThrownBy(() -> client(15000).complete(analyzeRequest()))
                    .isInstanceOfSatisfying(LlmException.class, e -> {
                        assertThat(e.getType()).isEqualTo(LlmException.Type.CLIENT_ERROR);
                        assertThat(e.isRetryable()).isFalse();
                    });
        }
    }

    @Test
    void 응답이_늦으면_TIMEOUT() {
        delayMs = 1000;
        responseBody = interaction("{}");

        assertThatThrownBy(() -> client(200).complete(analyzeRequest()))
                .isInstanceOfSatisfying(LlmException.class,
                        e -> assertThat(e.getType()).isEqualTo(LlmException.Type.TIMEOUT));
    }

    @Test
    void 연결이_안_되면_NETWORK() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        GeminiLlmClient client = new GeminiLlmClient(KEY, MODEL, 15000, "http://127.0.0.1:" + closedPort,
                objectMapper, resourceLoader);

        assertThatThrownBy(() -> client.complete(analyzeRequest()))
                .isInstanceOfSatisfying(LlmException.class,
                        e -> assertThat(e.getType()).isEqualTo(LlmException.Type.NETWORK));
    }

    @Test
    void AI_01은_LLM_TIMEOUT_MS를_다른_작업은_작업별_기본값을_쓴다() {
        GeminiLlmClient client = client(12000);

        assertThat(client.timeoutOf(analyzeRequest()).toMillis()).isEqualTo(12000);
        assertThat(client.timeoutOf(LlmRequest.of(LlmTask.SUMMARIZE_SHARE, "v1", "s", "u", null)))
                .isEqualTo(LlmTask.SUMMARIZE_SHARE.timeout());
    }

    @Test
    void 키나_모델이_비어_있으면_만들_수_없다() {
        assertThatThrownBy(() -> new GeminiLlmClient("", MODEL, 15000, "http://x", objectMapper, resourceLoader))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GeminiLlmClient(KEY, " ", 15000, "http://x", objectMapper, resourceLoader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(KEY);
    }

    @Test
    void live와_gemini일_때만_빈이_생기고_키가_없으면_기동_실패() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(GeminiLlmClient.class);

        runner.withPropertyValues("app.ai-mode=live", "app.llm.api-key=" + KEY, "app.llm.model=" + MODEL)
                .run(ctx -> assertThat(ctx).hasSingleBean(GeminiLlmClient.class));
        runner.withPropertyValues("app.ai-mode=mock", "app.llm.api-key=" + KEY, "app.llm.model=" + MODEL)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GeminiLlmClient.class));
        runner.withPropertyValues("app.ai-mode=live", "app.llm.provider=other", "app.llm.api-key=" + KEY,
                        "app.llm.model=" + MODEL)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GeminiLlmClient.class));
        runner.withPropertyValues("app.ai-mode=live")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 스키마의_enum이_공용_enum과_같다() throws IOException {
        JsonNode schema = objectMapper.readTree(
                resourceLoader.getResource("classpath:prompts/analyze_diary_v1.schema.json").getInputStream());
        JsonNode item = schema.path("properties").path("items").path("items").path("properties");

        assertThat(texts(schema.path("properties").path("safetyLevel").path("enum")))
                .containsExactlyInAnyOrderElementsOf(names(SafetyLevel.values()));
        assertThat(texts(item.path("category").path("enum")))
                .containsExactlyInAnyOrderElementsOf(names(ItemCategory.values()));
        assertThat(texts(item.path("timeSlot").path("anyOf").get(0).path("enum")))
                .containsExactlyInAnyOrderElementsOf(names(TimeSlot.values()));
        assertThat(texts(item.path("label").path("anyOf").get(0).path("enum")))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(BehaviorLabel.values()).map(BehaviorLabel::getText).toList());
    }

    private static List<String> texts(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> result.add(n.asText()));
        return result;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
