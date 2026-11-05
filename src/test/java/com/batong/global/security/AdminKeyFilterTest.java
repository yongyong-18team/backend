package com.batong.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminKeyFilterTest {

    private final ErrorResponseWriter writer = new ErrorResponseWriter(new ObjectMapper());

    private MockHttpServletResponse call(String serverKey, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/public-data/sync");
        if (header != null) {
            request.addHeader(AdminKeyFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AdminKeyFilter(serverKey, writer).doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void 서버_키가_비어_있으면_빈_헤더도_거부한다() throws Exception {
        assertThat(call("", "").getStatus()).isEqualTo(403);
        assertThat(call("", null).getStatus()).isEqualTo(403);
        assertThat(call(null, "").getStatus()).isEqualTo(403);
    }

    @Test
    void 키가_틀리거나_없으면_거부한다() throws Exception {
        MockHttpServletResponse response = call("test-admin-key", "wrong");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"COMMON_FORBIDDEN\"");
        assertThat(call("test-admin-key", null).getStatus()).isEqualTo(403);
    }

    @Test
    void 키가_같으면_통과한다() throws Exception {
        assertThat(call("test-admin-key", "test-admin-key").getStatus()).isEqualTo(200);
    }

    @Test
    void 관리자_경로가_아니면_검사하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/terms");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AdminKeyFilter("", writer).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
