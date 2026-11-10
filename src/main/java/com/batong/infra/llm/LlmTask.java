package com.batong.infra.llm;

import java.time.Duration;

/**
 * LLM 작업 목록과 작업별 기본 설정 (AI 명세 v1.4 1-1, 2-1).
 * MVP에서 실제로 호출하는 작업은 ANALYZE_DIARY 하나다.
 */
public enum LlmTask {

    ANALYZE_DIARY("AI-01", 0.2, 1500, Duration.ofSeconds(15)),
    SUMMARIZE_SHARE("AI-03", 0.3, 300, Duration.ofSeconds(10)),
    EXPLAIN_RECOMMENDATION("AI-04", 0.3, 150, Duration.ofSeconds(10)),
    SUMMARIZE_WEEKLY("AI-05", 0.3, 300, Duration.ofSeconds(10));

    private final String taskId;
    private final double temperature;
    private final int maxOutputTokens;
    private final Duration timeout;

    LlmTask(String taskId, double temperature, int maxOutputTokens, Duration timeout) {
        this.taskId = taskId;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
    }

    public String taskId() {
        return taskId;
    }

    public double temperature() {
        return temperature;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public Duration timeout() {
        return timeout;
    }
}
