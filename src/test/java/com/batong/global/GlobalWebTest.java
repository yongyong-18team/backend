package com.batong.global;

import com.batong.TestcontainersConfiguration;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.global.response.ApiResponse;
import com.batong.global.security.JwtProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 응답 형식, 에러 매핑, 인증, CORS가 실제 필터 체인에서 명세대로 동작하는지 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, GlobalWebTest.ProbeController.class})
class GlobalWebTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @RestController
    static class ProbeController {

        record Body(@NotBlank String name) {
        }

        @GetMapping("/api/v1/test-probe/me")
        ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal Long userId) {
            return ApiResponse.ok(Map.of("userId", userId, "at", Instant.parse("2026-11-03T12:30:00Z")));
        }

        @GetMapping("/api/v1/test-probe/business")
        ApiResponse<Void> business() {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        @GetMapping("/api/v1/test-probe/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("must not leak");
        }

        @PostMapping("/api/v1/test-probe/valid")
        ApiResponse<Void> valid(@Valid @RequestBody Body body) {
            return ApiResponse.ok(null);
        }
    }

    private String bearer() {
        return "Bearer " + jwtProvider.issue(7L);
    }

    @Test
    void 토큰이_없으면_401_COMMON_UNAUTHORIZED() throws Exception {
        mvc.perform(get("/api/v1/test-probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"));
    }

    @Test
    void 잘못된_토큰도_401() throws Exception {
        mvc.perform(get("/api/v1/test-probe/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_토큰이면_principal이_userId이고_시각은_KST_오프셋() throws Exception {
        mvc.perform(get("/api/v1/test-probe/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.at").value("2026-11-03T21:30:00+09:00"));
    }

    @Test
    void BusinessException은_코드의_HTTP_상태와_메시지로() throws Exception {
        mvc.perform(get("/api/v1/test-probe/business").header("Authorization", bearer()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("SHARE_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.SHARE_EXPIRED.getMessage()));
    }

    @Test
    void 예상하지_못한_예외는_500_COMMON_INTERNAL_ERROR이고_메시지를_노출하지_않는다() throws Exception {
        mvc.perform(get("/api/v1/test-probe/boom").header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.COMMON_INTERNAL_ERROR.getMessage()));
    }

    @Test
    void 검증_실패와_허용되지_않은_메서드는_400_COMMON_VALIDATION() throws Exception {
        mvc.perform(post("/api/v1/test-probe/valid").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        mvc.perform(post("/api/v1/test-probe/valid").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        mvc.perform(post("/api/v1/test-probe/me").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
    }

    @Test
    void 없는_경로는_404_COMMON_NOT_FOUND() throws Exception {
        mvc.perform(get("/api/v1/no-such-path").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
    }

    @Test
    void 관리자_키가_설정되지_않으면_관리자_API는_403() throws Exception {
        mvc.perform(post("/api/v1/admin/public-data/sync").header("X-Admin-Key", ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void 허용한_프론트_origin의_preflight를_통과시킨다() throws Exception {
        mvc.perform(options("/api/v1/test-probe/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/test-probe/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
