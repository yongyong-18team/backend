package com.batong.analysis.service;

/**
 * AI-01 응답을 쓸 수 없음(JSON 아님, safetyLevel 없음). AI 명세 2-1의 "즉시 실패"라 1회 재시도 대상이다.
 * 메시지에 응답 본문을 넣지 않는다(AI 명세 2-6).
 */
public class AnalysisParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AnalysisParseException(String message) {
        super(message);
    }
}
