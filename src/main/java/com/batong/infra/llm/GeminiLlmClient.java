package com.batong.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI_MODE=live, LLM_PROVIDER=gemini일 때 쓰는 LlmClient (TRD 8-6).
 *
 * <p>Interactions API {@code POST /v1beta/interactions}를 한 번 호출한다.
 * <ul>
 *   <li>구조화 출력: {@code prompts/{작업}_{버전}.schema.json}이 있으면 {@code response_format}으로 보낸다.</li>
 *   <li>{@code store=false}: 일기가 공급자 쪽에 저장되지 않게 한다(API 기본값은 저장).</li>
 *   <li>temperature: Interactions API의 generation_config에 필드가 없어 보내지 않는다.</li>
 *   <li>thinking은 끌 수 없어 {@code minimal}로 둔다. thinking 토큰도 max_output_tokens에 들어간다.</li>
 * </ul>
 *
 * <p>요청·응답 본문과 API 키는 로그·예외 메시지에 넣지 않는다 (AI 명세 2-6, TRD 13장).
 */
@Component
@ConditionalOnExpression("'${app.ai-mode:mock}' == 'live' and '${app.llm.provider:gemini}' == 'gemini'")
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    static final String PATH = "/v1beta/interactions";
    static final String SCHEMA_PATH = "classpath:prompts/%s_%s.schema.json";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final String model;
    private final Duration analyzeDiaryTimeout;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final HttpClient httpClient;
    /** 타임아웃별 RestClient. 읽기 타임아웃이 요청 팩토리 단위라 나눠 둔다(실제로는 AI-01 하나) */
    private final Map<Duration, RestClient> clients = new ConcurrentHashMap<>();
    private final Map<String, Optional<JsonNode>> schemas = new ConcurrentHashMap<>();

    public GeminiLlmClient(@Value("${app.llm.api-key:}") String apiKey,
                           @Value("${app.llm.model:}") String model,
                           @Value("${app.llm.timeout-ms:15000}") long timeoutMs,
                           @Value("${app.llm.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                           ObjectMapper objectMapper,
                           ResourceLoader resourceLoader) {
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            // 값은 로그에 남기지 않는다
            throw new IllegalStateException("AI_MODE=live requires LLM_API_KEY and LLM_MODEL");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.analyzeDiaryTimeout = Duration.ofMillis(timeoutMs);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        log.info("GeminiLlmClient 사용. model={}, AI-01 timeout={}ms", model, timeoutMs);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String body = requestBody(request).toString();
        long start = System.nanoTime();
        String responseBody;
        try {
            responseBody = client(timeoutOf(request)).post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status >= 500) {
                            throw new LlmException(LlmException.Type.SERVER_ERROR, "Gemini HTTP " + status);
                        }
                        if (status >= 400) {
                            throw new LlmException(LlmException.Type.CLIENT_ERROR, "Gemini HTTP " + status);
                        }
                        try (InputStream in = res.getBody()) {
                            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }, true);
        } catch (ResourceAccessException e) {
            LlmException.Type type = isTimeout(e) ? LlmException.Type.TIMEOUT : LlmException.Type.NETWORK;
            throw new LlmException(type, "Gemini " + type.name().toLowerCase(Locale.ROOT), e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new LlmResponse(outputText(responseBody, request, latencyMs), model, latencyMs);
    }

    ObjectNode requestBody(LlmRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("system_instruction", request.systemPrompt());
        root.put("input", request.userPrompt());
        root.put("store", false);

        ObjectNode config = root.putObject("generation_config");
        config.put("max_output_tokens", request.maxOutputTokens());
        config.put("thinking_level", "minimal");
        config.put("thinking_summaries", "none");

        ObjectNode format = root.putObject("response_format");
        format.put("type", "text");
        format.put("mime_type", "application/json");
        schema(request).ifPresent(schema -> format.set("schema", schema));
        return root;
    }

    /** AI-01은 LLM_TIMEOUT_MS를 쓰고, 나머지 작업은 작업별 기본값(AI 명세 2-1)을 쓴다. */
    Duration timeoutOf(LlmRequest request) {
        return request.task() == LlmTask.ANALYZE_DIARY ? analyzeDiaryTimeout : request.timeout();
    }

    /** steps 중 model_output의 text를 이어 붙인다. thought 단계는 버린다. 파싱·검증은 analysis가 한다. */
    private String outputText(String responseBody, LlmRequest request, long latencyMs) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new LlmException(LlmException.Type.SERVER_ERROR, "Gemini 응답이 JSON이 아님");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode step : root.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asText())) {
                    text.append(content.path("text").asText(""));
                }
            }
        }
        JsonNode usage = root.path("usage");
        log.info("Gemini 응답 task={} status={} latencyMs={} inputTokens={} outputTokens={} thoughtTokens={}",
                request.task().taskId(), root.path("status").asText("?"), latencyMs,
                usage.path("total_input_tokens").asInt(-1), usage.path("total_output_tokens").asInt(-1),
                usage.path("total_thought_tokens").asInt(-1));
        return text.toString();
    }

    private Optional<JsonNode> schema(LlmRequest request) {
        String name = request.task().name().toLowerCase(Locale.ROOT);
        return schemas.computeIfAbsent(name + "_" + request.promptVersion(),
                key -> readSchema(name, request.promptVersion()));
    }

    private Optional<JsonNode> readSchema(String name, String version) {
        Resource resource = resourceLoader.getResource(SCHEMA_PATH.formatted(name, version));
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(objectMapper.readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("스키마 파일을 읽지 못함: " + SCHEMA_PATH.formatted(name, version), e);
        }
    }

    private RestClient client(Duration timeout) {
        return clients.computeIfAbsent(timeout, t -> {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(t);
            return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        });
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
