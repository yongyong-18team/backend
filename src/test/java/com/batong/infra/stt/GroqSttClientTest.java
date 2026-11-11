package com.batong.infra.stt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 가짜 Groq(JDK 내장 HTTP 서버)로 요청 모양·에러 변환·빈 생성 조건을 확인한다. 실제 키를 쓰지 않는다 */
class GroqSttClientTest {

    static final String KEY = "test-key-not-real";
    static final String MODEL = "whisper-large-v3";
    static final String SECRET_BODY = "가상 공급자 오류 본문";
    static final byte[] AUDIO = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    final ObjectMapper objectMapper = new ObjectMapper();

    HttpServer server;
    final AtomicReference<String> receivedBody = new AtomicReference<>();
    final AtomicReference<String> receivedAuth = new AtomicReference<>();
    final AtomicReference<String> receivedPath = new AtomicReference<>();
    final AtomicReference<String> receivedContentType = new AtomicReference<>();
    volatile int status = 200;
    volatile String responseBody = "{}";
    volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
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

    GroqSttClient client(Duration readTimeout) {
        return new GroqSttClient(KEY, MODEL, "http://127.0.0.1:" + server.getAddress().getPort(), readTimeout, objectMapper);
    }

    static String transcription(String text) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of("text", text));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 요청은_키_헤더와_multipart_file_model_language로_간다() {
        responseBody = transcription("오늘 민지가 산책을 했다.");

        String text = client(Duration.ofSeconds(5)).transcribe(AUDIO, "audio/webm");

        assertThat(text).isEqualTo("오늘 민지가 산책을 했다.");
        assertThat(receivedPath.get()).isEqualTo("/openai/v1/audio/transcriptions");
        assertThat(receivedAuth.get()).isEqualTo("Bearer " + KEY);
        assertThat(receivedContentType.get()).startsWith("multipart/form-data");
        String body = receivedBody.get();
        assertThat(body).contains("name=\"file\"; filename=\"audio.webm\"").contains("Content-Type: audio/webm");
        assertThat(body).contains("name=\"model\"").contains(MODEL);
        assertThat(body).contains("name=\"language\"").contains("\r\n\r\nko\r\n");
        assertThat(body).contains("name=\"response_format\"");
        assertThat(body).contains(new String(AUDIO, StandardCharsets.ISO_8859_1));
        assertThat(body).doesNotContain(KEY);
    }

    @Test
    void 형식마다_맞는_파일_이름을_붙인다() {
        responseBody = transcription("가상 문장");
        for (String type : new String[]{"audio/mp4", "audio/m4a"}) {
            client(Duration.ofSeconds(5)).transcribe(AUDIO, type);
            String ext = type.substring("audio/".length());
            assertThat(receivedBody.get()).contains("filename=\"audio." + ext + "\"").contains("Content-Type: " + type);
        }
    }

    @Test
    void 결과_앞뒤_공백은_지운다() {
        responseBody = transcription("  가상 문장입니다.\n");

        assertThat(client(Duration.ofSeconds(5)).transcribe(AUDIO, "audio/mp4")).isEqualTo("가상 문장입니다.");
    }

    @Test
    void 공급자_오류는_FAILED이고_메시지에_본문과_키가_없다() {
        responseBody = SECRET_BODY;
        for (int code : new int[]{400, 401, 413, 429, 500, 503}) {
            status = code;
            assertThatThrownBy(() -> client(Duration.ofSeconds(5)).transcribe(AUDIO, "audio/webm"))
                    .isInstanceOfSatisfying(SttException.class, e -> {
                        assertThat(e.getType()).isEqualTo(SttException.Type.FAILED);
                        assertThat(e.getMessage()).doesNotContain(SECRET_BODY).doesNotContain(KEY);
                    });
        }
    }

    @Test
    void 빈_결과나_JSON이_아닌_응답은_FAILED() {
        for (String body : new String[]{transcription(""), transcription("   "), "{}", "not json"}) {
            responseBody = body;
            assertThatThrownBy(() -> client(Duration.ofSeconds(5)).transcribe(AUDIO, "audio/webm"))
                    .isInstanceOfSatisfying(SttException.class,
                            e -> assertThat(e.getType()).isEqualTo(SttException.Type.FAILED));
        }
    }

    @Test
    void 응답이_늦으면_TIMEOUT() {
        delayMs = 1000;
        responseBody = transcription("가상 문장");

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).transcribe(AUDIO, "audio/webm"))
                .isInstanceOfSatisfying(SttException.class,
                        e -> assertThat(e.getType()).isEqualTo(SttException.Type.TIMEOUT));
    }

    @Test
    void 연결이_안_되면_FAILED() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        GroqSttClient client = new GroqSttClient(KEY, MODEL, "http://127.0.0.1:" + closedPort, Duration.ofSeconds(5), objectMapper);

        assertThatThrownBy(() -> client.transcribe(AUDIO, "audio/webm"))
                .isInstanceOfSatisfying(SttException.class,
                        e -> assertThat(e.getType()).isEqualTo(SttException.Type.FAILED));
    }

    @Test
    void 기본_읽기_타임아웃은_20초다() {
        assertThat(GroqSttClient.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void 키가_비어_있으면_만들_수_없다() {
        for (String key : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> new GroqSttClient(key, MODEL, "http://x", Duration.ofSeconds(1), objectMapper))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void live와_groq일_때만_빈이_생기고_키가_없으면_기동_실패() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(GroqSttClient.class, MockSttClient.class);

        runner.withPropertyValues("app.ai-mode=live", "app.stt.api-key=" + KEY)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SttClient.class);
                    assertThat(ctx).hasSingleBean(GroqSttClient.class);
                });
        runner.withPropertyValues("app.ai-mode=mock", "app.stt.api-key=" + KEY)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(GroqSttClient.class);
                    assertThat(ctx).hasSingleBean(MockSttClient.class);
                });
        runner.withPropertyValues("app.ai-mode=live", "app.stt.provider=other", "app.stt.api-key=" + KEY)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SttClient.class));
        runner.withPropertyValues("app.ai-mode=live")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
