package com.batong.infra.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * AI_MODE=live, STT_PROVIDER=groq일 때 쓰는 SttClient (TRD 8-2).
 *
 * <p>Groq {@code POST /openai/v1/audio/transcriptions}에 multipart로 {@code file}, {@code model}, {@code language=ko}를 보낸다.
 * Groq는 파일 이름 확장자로 형식을 판단하므로 형식에 맞는 이름을 붙인다. 형식 변환은 하지 않는다.
 * 읽기 타임아웃 20초, 재시도 없음.
 *
 * <p>음성·변환 텍스트·응답 본문·API 키는 로그와 예외 메시지에 넣지 않는다 (TRD 13장).
 */
@Component
@ConditionalOnExpression("'${app.ai-mode:mock}' == 'live' and '${app.stt.provider:groq}' == 'groq'")
public class GroqSttClient implements SttClient {

    private static final Logger log = LoggerFactory.getLogger(GroqSttClient.class);

    static final String DEFAULT_BASE_URL = "https://api.groq.com";
    static final String PATH = "/openai/v1/audio/transcriptions";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    /** SttAudioFormat이 정규화한 MIME 타입 → Groq에 보낼 파일 이름 */
    static final Map<String, String> FILE_NAMES = Map.of(
            "audio/webm", "audio.webm",
            "audio/mp4", "audio.mp4",
            "audio/m4a", "audio.m4a");

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public GroqSttClient(@Value("${app.stt.api-key:}") String apiKey,
                         @Value("${app.stt.model:whisper-large-v3}") String model,
                         @Value("${app.stt.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                         ObjectMapper objectMapper) {
        this(apiKey, model, baseUrl, READ_TIMEOUT, objectMapper);
    }

    GroqSttClient(String apiKey, String model, String baseUrl, Duration readTimeout, ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            // 값은 로그에 남기지 않는다
            throw new IllegalStateException("AI_MODE=live requires STT_API_KEY");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        log.info("GroqSttClient 사용. model={}", model);
    }

    @Override
    public String transcribe(byte[] audio, String contentType) {
        String fileName = FILE_NAMES.get(contentType);
        if (fileName == null) {
            // 컨트롤러가 먼저 걸러서 오지 않는 값
            throw new SttException(SttException.Type.FAILED, "지원하지 않는 형식");
        }
        // MultipartBodyBuilder는 reactive-streams가 있어야 해서 MultiValueMap으로 직접 만든다
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        fileHeaders.setContentDispositionFormData("file", fileName);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(new ByteArrayResource(audio), fileHeaders));
        parts.add("model", model);
        parts.add("language", "ko");
        parts.add("response_format", "json");

        long start = System.nanoTime();
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status >= 400) {
                            throw new SttException(SttException.Type.FAILED, "Groq HTTP " + status);
                        }
                        try (InputStream in = res.getBody()) {
                            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }, true);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new SttException(SttException.Type.TIMEOUT, "Groq timeout", e);
            }
            throw new SttException(SttException.Type.FAILED, "Groq network error", e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        String text = text(responseBody);
        log.info("Groq STT 응답 latencyMs={} audioBytes={} format={} textLength={}",
                latencyMs, audio.length, contentType, text.length());
        if (text.isBlank()) {
            throw new SttException(SttException.Type.FAILED, "Groq 결과가 비어 있음");
        }
        return text;
    }

    private String text(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new SttException(SttException.Type.FAILED, "Groq 응답이 JSON이 아님");
        }
        return root.path("text").asText("").strip();
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
