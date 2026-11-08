package com.batong.infra.llm;

import java.time.Duration;
import java.util.Objects;

/**
 * LLM 호출 한 번의 입력.
 *
 * @param task            작업 종류 (로그·Mock 분기에 사용)
 * @param promptVersion   프롬프트 버전 (예: "v1"). ai_analysis.prompt_version에 기록
 * @param systemPrompt    시스템 프롬프트
 * @param userPrompt      사용자 프롬프트. AI-01이면 마스킹된 일기가 들어 있다
 * @param temperature     AI 명세 2-1
 * @param maxOutputTokens AI 명세 2-1
 * @param timeout         이 호출 한 번의 타임아웃
 * @param mockKey         Mock 모드에서 평가셋 사례를 찾는 키. 원문 정규화 SHA-256 해시({@link LlmMockKey}).
 *                        실제 공급자 구현체는 이 값을 쓰지 않는다. 원문 자체는 넣지 않는다. 없으면 null
 */
public record LlmRequest(
        LlmTask task,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxOutputTokens,
        Duration timeout,
        String mockKey
) {

    public LlmRequest {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        Objects.requireNonNull(timeout, "timeout");
    }

    /** 작업 기본 설정(temperature, 토큰, 타임아웃)으로 만든다. */
    public static LlmRequest of(LlmTask task, String promptVersion, String systemPrompt,
                                String userPrompt, String mockKey) {
        return new LlmRequest(task, promptVersion, systemPrompt, userPrompt,
                task.temperature(), task.maxOutputTokens(), task.timeout(), mockKey);
    }

    /** 일기 본문이 로그에 찍히지 않도록 프롬프트는 길이만 남긴다. */
    @Override
    public String toString() {
        return "LlmRequest[task=" + task.taskId()
                + ", promptVersion=" + promptVersion
                + ", systemPromptLength=" + systemPrompt.length()
                + ", userPromptLength=" + userPrompt.length()
                + ", timeout=" + timeout + "]";
    }
}
