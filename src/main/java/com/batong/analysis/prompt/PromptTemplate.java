package com.batong.analysis.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프롬프트 파일 하나 (resources/prompts/{작업}_v{n}.txt).
 *
 * <p>파일 형식: {@code ### SYSTEM} 줄 아래가 시스템 프롬프트, {@code ### USER} 줄 아래가 사용자 프롬프트.
 * 사용자 프롬프트의 {@code {이름}} 자리표시자를 값으로 바꾼다.
 */
public record PromptTemplate(String name, String version, String system, String user) {

    static final String SYSTEM_MARKER = "### SYSTEM";
    static final String USER_MARKER = "### USER";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    static PromptTemplate parse(String name, String version, String raw) {
        String text = raw.replace("\r\n", "\n");
        int systemIdx = text.indexOf(SYSTEM_MARKER + "\n");
        int userIdx = text.indexOf("\n" + USER_MARKER + "\n");
        if (systemIdx != 0 || userIdx < 0) {
            throw new IllegalStateException(
                    "프롬프트 형식 오류(%s_%s): 첫 줄 '%s', 이어서 '%s' 줄이 있어야 합니다"
                            .formatted(name, version, SYSTEM_MARKER, USER_MARKER));
        }
        String system = text.substring(SYSTEM_MARKER.length() + 1, userIdx).strip();
        String user = text.substring(userIdx + USER_MARKER.length() + 2).strip();
        return new PromptTemplate(name, version, system, user);
    }

    /**
     * 사용자 프롬프트의 자리표시자를 한 번에 바꾼다.
     * 넣은 값은 다시 검사하지 않으므로, 일기 본문에 "{diaryDate}" 같은 글자가 있어도 그대로 남는다.
     *
     * @throws IllegalArgumentException 값이 없는 자리표시자가 있을 때
     */
    public String renderUser(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(user);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException("프롬프트 값 없음: " + key);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
