package com.batong.global.security;

import com.batong.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** /api/v1/admin/** 는 X-Admin-Key가 ADMIN_API_KEY와 같을 때만 통과. 서버 키가 비어 있으면 항상 거부한다. */
public class AdminKeyFilter extends OncePerRequestFilter {

    static final String PATH_PREFIX = "/api/v1/admin/";
    static final String HEADER = "X-Admin-Key";

    private final byte[] adminKey;
    private final ErrorResponseWriter errorWriter;

    public AdminKeyFilter(String adminKey, ErrorResponseWriter errorWriter) {
        this.adminKey = adminKey == null ? new byte[0] : adminKey.getBytes(StandardCharsets.UTF_8);
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        // 빈 서버 키와 빈 헤더를 같다고 보면 안 되므로 서버 키가 비어 있는지 먼저 본다
        boolean allowed = adminKey.length > 0 && given != null
                && MessageDigest.isEqual(adminKey, given.getBytes(StandardCharsets.UTF_8));
        if (!allowed) {
            errorWriter.write(response, ErrorCode.COMMON_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
