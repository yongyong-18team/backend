package com.batong.analysis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 서버 위기 키워드 검사 (AI 명세 3-5). LLM 호출 전에 원문으로 한다.
 *
 * <p>사전 파일은 한 줄에 하나, {@code #}로 시작하는 줄은 주석이다. 원문과 사전 항목 모두 공백을 지우고 포함 여부를 본다.
 * 브라우저·OS에 따라 한글 자모가 분리되어 들어오는 경우를 대비해 NFC 정규화를 먼저 한다(놓치는 쪽보다 잡는 쪽이 안전).
 */
@Component
public class CrisisKeywordChecker {

    static final String PATH = "classpath:prompts/crisis_keywords_v2.txt";
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final List<String> keywords;

    @Autowired
    public CrisisKeywordChecker(ResourceLoader resourceLoader) {
        this(read(resourceLoader));
    }

    CrisisKeywordChecker(List<String> rawKeywords) {
        this.keywords = rawKeywords.stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(CrisisKeywordChecker::normalize)
                .filter(keyword -> !keyword.isEmpty())
                .toList();
        if (keywords.isEmpty()) {
            throw new IllegalStateException("위기 키워드 사전이 비어 있습니다: " + PATH);
        }
    }

    /** 원문(마스킹 전)에 사전 항목이 하나라도 들어 있으면 true */
    public boolean matches(String originalText) {
        String text = normalize(originalText);
        return keywords.stream().anyMatch(text::contains);
    }

    int size() {
        return keywords.size();
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll("");
    }

    private static List<String> read(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(PATH);
        if (!resource.exists()) {
            throw new IllegalStateException("위기 키워드 사전이 없습니다: " + PATH);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException("위기 키워드 사전을 읽지 못했습니다: " + PATH, e);
        }
    }
}
