package com.batong;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 백엔드 A 스텁: 경로·HTTP 상태·인증 여부·응답 모양이 API 명세와 같은지. 실제 구현이 들어오면 도메인별 테스트로 옮긴다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class BackendAStubTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private String loginToken() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"mom1234\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn").value(604800))
                .andExpect(jsonPath("$.data.subjects[0].role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private void call(HttpMethod method, String path, String json, int expectedStatus, String token) throws Exception {
        var req = request(method, path).header("Authorization", token);
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        mvc.perform(req).andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 공개_API는_토큰_없이_호출된다() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"mom1234\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(1));
        mvc.perform(get("/api/v1/auth/login-id/check").param("loginId", "mom1234"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/shares/any-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.content.basicInfo.name").value("민지"));
    }

    @Test
    void 로그인으로_받은_토큰으로_보호_API_스텁을_모두_호출할_수_있다() throws Exception {
        String t = loginToken();

        call(HttpMethod.GET, "/api/v1/users/me", null, 200, t);
        call(HttpMethod.PATCH, "/api/v1/users/me", "{\"displayName\":\"김영희\",\"relation\":\"PARENT\"}", 200, t);
        call(HttpMethod.DELETE, "/api/v1/users/me", null, 200, t);
        call(HttpMethod.GET, "/api/v1/subjects/10/home", null, 200, t);

        call(HttpMethod.POST, "/api/v1/subjects/10/guide/entries", "{\"section\":\"PROHIBITED\",\"content\":\"가상 항목\"}", 201, t);
        call(HttpMethod.PATCH, "/api/v1/subjects/10/guide/entries/310", "{\"content\":\"가상 항목\"}", 200, t);
        call(HttpMethod.DELETE, "/api/v1/subjects/10/guide/entries/310", null, 200, t);
        call(HttpMethod.POST, "/api/v1/subjects/10/diaries/501/items/review",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\"}]}", 200, t);

        call(HttpMethod.GET, "/api/v1/subjects/10/shares/section-options?documentType=GUIDE", null, 200, t);
        call(HttpMethod.POST, "/api/v1/subjects/10/shares/drafts",
                "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"BEHAVIOR_RESPONSE\"],\"showEmergencyContact\":true}", 201, t);
        call(HttpMethod.POST, "/api/v1/subjects/10/shares/900/approve", null, 200, t);
        call(HttpMethod.GET, "/api/v1/subjects/10/shares", null, 200, t);
        call(HttpMethod.POST, "/api/v1/subjects/10/shares/900/revoke", null, 200, t);

        call(HttpMethod.GET, "/api/v1/users/lookup?loginId=dad5678", null, 200, t);
        call(HttpMethod.POST, "/api/v1/subjects/10/invitations", "{\"inviteeLoginId\":\"dad5678\"}", 201, t);
        call(HttpMethod.POST, "/api/v1/subjects/10/invitations/70/cancel", null, 200, t);
        call(HttpMethod.GET, "/api/v1/users/me/invitations", null, 200, t);
        call(HttpMethod.POST, "/api/v1/users/me/invitations/70/accept", null, 200, t);
        call(HttpMethod.POST, "/api/v1/users/me/invitations/70/reject", null, 200, t);
        call(HttpMethod.GET, "/api/v1/subjects/10/members", null, 200, t);
        call(HttpMethod.DELETE, "/api/v1/subjects/10/members/2", null, 200, t);
    }

    @Test
    void 설명서는_7개_섹션을_PRD_순서로_주고_시각은_KST() throws Exception {
        mvc.perform(get("/api/v1/subjects/10/guide").header("Authorization", loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections", hasSize(7)))
                .andExpect(jsonPath("$.data.sections[0].section").value("PROHIBITED"))
                .andExpect(jsonPath("$.data.sections[6].section").value("PREFERENCE"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-03T21:10:00+09:00"));
    }

    @Test
    void 보호_API는_토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/subjects/10/guide")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/lookup").param("loginId", "dad5678")).andExpect(status().isUnauthorized());
    }
}
