package com.batong.infra.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Mock 모드에서 평가셋 사례를 찾는 키 (AI 명세 8장 "입력 원문의 정규화 해시").
 *
 * <p>정규화는 근거 문장 검증(AI 명세 2-4)과 같은 규칙이다: 공백 제거, 문장부호 {@code .,!?~…'"“”‘’} 제거.
 * 여기에 유니코드 NFC 정규화를 먼저 한다(브라우저·OS에 따라 한글 자모가 분리되어 들어오는 경우 대비).
 * 그래서 시연 중 띄어쓰기나 마침표가 조금 달라져도 같은 Mock 응답이 나온다.
 */
public final class LlmMockKey {

    private static final Pattern WHITESPACE_AND_PUNCT = Pattern.compile("[\\s.,!?~…'\"“”‘’]");

    private LlmMockKey() {
    }

    /** 일기 원문(마스킹 전)을 넣는다. */
    public static String of(String originalText) {
        if (originalText == null) {
            return null;
        }
        return sha256Hex(normalize(originalText));
    }

    static String normalize(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        return WHITESPACE_AND_PUNCT.matcher(nfc).replaceAll("");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
