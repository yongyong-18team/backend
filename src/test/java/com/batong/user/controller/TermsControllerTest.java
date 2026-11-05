package com.batong.user.controller;

import com.batong.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 응답이 API 명세 TERMS-01 예시와 같은 모양인지, 토큰 없이 호출되는지 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class TermsControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 토큰_없이_동의_항목_4개를_명세_순서대로_준다() throws Exception {
        mvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.terms[*].consentType")
                        .value(contains("PRIVACY", "SENSITIVE_INFO", "AI_PROCESSING", "SUBJECT_AUTHORITY")))
                .andExpect(jsonPath("$.data.terms[*].termsVersion").value(everyItem(is("1.0"))))
                .andExpect(jsonPath("$.data.terms[*].required").value(everyItem(is(true))))
                .andExpect(jsonPath("$.data.terms[0].title").value("개인정보 수집·이용"))
                .andExpect(jsonPath("$.data.terms[0].content").isString());
    }
}
