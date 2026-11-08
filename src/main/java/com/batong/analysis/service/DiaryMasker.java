package com.batong.analysis.service;

import java.util.regex.Pattern;

/**
 * LLM 전송 전 마스킹과 저장 전 복원 (AI 명세 2-2, 2-3-4).
 *
 * <p>대상자 이름을 "대상자"로, 전화번호를 "[연락처]"로 바꾼다. 비상 연락처는 애초에 넣지 않는다(호출하는 쪽이 일기 본문만 넘긴다).
 * 다른 사람의 이름(활동지원사, 가족)은 MVP에서 처리하지 않는다(AI 명세 2-2).
 */
public final class DiaryMasker {

    public static final String SUBJECT_PLACEHOLDER = "대상자";
    public static final String PHONE_PLACEHOLDER = "[연락처]";

    /** AI 명세 2-2의 두 패턴. 휴대전화가 지역번호 패턴보다 먼저 맞도록 앞에 둔다 */
    private static final Pattern PHONE = Pattern.compile("01[016789]-?\\d{3,4}-?\\d{4}|0\\d{1,2}-?\\d{3,4}-?\\d{4}");

    private DiaryMasker() {
    }

    /** 조사가 붙어 있어도 이름 부분만 바뀐다("민지가" → "대상자가") */
    public static String mask(String text, String subjectName) {
        String withoutName = text.replace(subjectName, SUBJECT_PLACEHOLDER);
        return PHONE.matcher(withoutName).replaceAll(PHONE_PLACEHOLDER);
    }

    /** 저장 전에 "대상자"를 원래 이름으로 되돌린다. 지운 전화번호는 되돌리지 않는다 */
    public static String unmask(String text, String subjectName) {
        return text.replace(SUBJECT_PLACEHOLDER, subjectName);
    }
}
