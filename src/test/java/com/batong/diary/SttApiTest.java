package com.batong.diary;

import com.batong.TestcontainersConfiguration;
import com.batong.global.security.JwtProvider;
import com.batong.infra.stt.SttClient;
import com.batong.infra.stt.SttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** STT-01: 받는 형식(코덱 파라미터 포함), 빈 파일·잘못된 형식 400, 10MB 초과 413, 공급자 실패 502, 로그인 필요 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class SttApiTest {

    static final String TEXT = "오늘 오후에 민지와 공원을 산책했다.";

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;
    @MockitoBean
    SttClient sttClient;

    String token;

    @BeforeEach
    void seed() {
        long userId = jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES ('stt_user', 'x', now(), now()) RETURNING user_id")
                .query(Long.class).single();
        token = "Bearer " + jwt.issue(userId);
        when(sttClient.transcribe(any(), anyString())).thenReturn(TEXT);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'stt\\_%'").update();
    }

    private ResultActions upload(String auth, MockMultipartFile file) throws Exception {
        var req = multipart("/api/v1/stt").file(file);
        if (auth != null) {
            req.header("Authorization", auth);
        }
        return mvc.perform(req);
    }

    private static MockMultipartFile audio(String contentType, byte[] bytes) {
        return new MockMultipartFile("audio", "rec", contentType, bytes);
    }

    @Test
    void 브라우저_녹음_형식을_그대로_받아_텍스트를_준다() throws Exception {
        String[][] cases = {
                {"audio/webm", "audio/webm"},
                {"audio/webm;codecs=opus", "audio/webm"},
                {"AUDIO/MP4", "audio/mp4"},
                {"audio/mp4; codecs=mp4a.40.2", "audio/mp4"},
                {"audio/m4a", "audio/m4a"}};
        byte[] bytes = {1, 2, 3};
        for (String[] c : cases) {
            upload(token, audio(c[0], bytes))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.text").value(TEXT));
        }
        // 코덱 파라미터를 떼고 소문자로 바꾼 값으로 공급자를 부른다
        verify(sttClient, times(2)).transcribe(bytes, "audio/webm");
        verify(sttClient, times(2)).transcribe(bytes, "audio/mp4");
        verify(sttClient, times(1)).transcribe(bytes, "audio/m4a");
    }

    @Test
    void 빈_파일이나_받지_않는_형식은_400이고_공급자를_부르지_않는다() throws Exception {
        upload(token, audio("audio/webm", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STT_UNSUPPORTED_FORMAT"));
        for (String type : new String[]{"audio/mpeg", "audio/wav", "video/webm", "text/plain", "application/octet-stream"}) {
            upload(token, audio(type, new byte[]{1}))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("STT_UNSUPPORTED_FORMAT"));
        }
        verify(sttClient, never()).transcribe(any(), anyString());
    }

    @Test
    void 필드_이름이_다르면_400() throws Exception {
        mvc.perform(multipart("/api/v1/stt").file(new MockMultipartFile("file", "rec", "audio/webm", new byte[]{1}))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 십MB를_넘으면_413이고_딱_10MB는_받는다() throws Exception {
        upload(token, audio("audio/webm", new byte[10 * 1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("STT_FILE_TOO_LARGE"));
        verify(sttClient, never()).transcribe(any(), anyString());

        upload(token, audio("audio/webm", new byte[10 * 1024 * 1024])).andExpect(status().isOk());
    }

    @Test
    void 공급자가_실패하면_502_STT_FAILED() throws Exception {
        for (SttException.Type type : SttException.Type.values()) {
            when(sttClient.transcribe(any(), eq("audio/webm"))).thenThrow(new SttException(type, "가상 실패"));
            upload(token, audio("audio/webm", new byte[]{1}))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("STT_FAILED"));
        }
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        upload(null, audio("audio/webm", new byte[]{1})).andExpect(status().isUnauthorized());
        verify(sttClient, never()).transcribe(any(), anyString());
    }
}
