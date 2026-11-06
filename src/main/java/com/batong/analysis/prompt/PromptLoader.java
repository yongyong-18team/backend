package com.batong.analysis.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * resources/prompts/{name}_{version}.txt 를 읽어 캐시한다 (AI 명세 2-5).
 */
@Component
public class PromptLoader {

    private static final String PATH = "classpath:prompts/%s_%s.txt";

    private final ResourceLoader resourceLoader;
    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * @param name    작업 이름 (예: "analyze_diary")
     * @param version 버전 (예: "v1")
     */
    public PromptTemplate load(String name, String version) {
        return cache.computeIfAbsent(name + "_" + version, key -> read(name, version));
    }

    private PromptTemplate read(String name, String version) {
        Resource resource = resourceLoader.getResource(PATH.formatted(name, version));
        if (!resource.exists()) {
            throw new IllegalStateException("프롬프트 파일 없음: " + PATH.formatted(name, version));
        }
        try (InputStream in = resource.getInputStream()) {
            return PromptTemplate.parse(name, version, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽지 못함: " + PATH.formatted(name, version), e);
        }
    }
}
