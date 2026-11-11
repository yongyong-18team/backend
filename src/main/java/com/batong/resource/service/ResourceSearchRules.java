package com.batong.resource.service;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import java.util.Map;

/** RES-02 검색 규칙 중 DB 없이 판단하는 부분 (TRD 9-3). 서버 규칙이고 LLM은 쓰지 않는다 */
public final class ResourceSearchRules {

    private ResourceSearchRules() {
    }

    /** 요청 careTerm이 없으면 모든 제도, 있으면 같은 값이거나 BOTH인 제도 */
    public static boolean matchesCareTerm(CareTerm programTerm, CareTerm requested) {
        return requested == null || programTerm == requested || programTerm == CareTerm.BOTH;
    }

    /**
     * target_conditions의 minAge(이상), maxAge(미만)로 나이를 판단한다 (#84).
     * birth_year만 있어 만 나이가 yearAge 또는 yearAge-1 중 하나다. 그 범위에 조건을 만족하는 나이가
     * 하나라도 있으면 포함한다(경계가 모호한 사람은 제도에서 빼지 않는다).
     *
     * @param yearAge 올해 - 출생 연도 (CareSubject.age())
     */
    public static boolean matchesAge(Map<String, Object> conditions, int yearAge) {
        int highest = yearAge;
        int lowest = Math.max(yearAge - 1, 0);
        Integer minAge = intValue(conditions, "minAge");
        Integer maxAge = intValue(conditions, "maxAge");
        if (minAge != null && highest < minAge) {
            return false;
        }
        return maxAge == null || lowest < maxAge;
    }

    private static Integer intValue(Map<String, Object> conditions, String key) {
        Object value = conditions == null ? null : conditions.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("target_conditions." + key + "는 숫자여야 합니다: " + value);
    }

    /** 제도 추천 이유 (서버 응답 문구 확정안 4-2, 7장) */
    public static String programReason(CareTerm requested) {
        if (requested == CareTerm.SHORT) {
            return "단기 돌봄과 관련된 제도예요.";
        }
        if (requested == CareTerm.LONG) {
            return "장기 돌봄과 관련된 제도예요.";
        }
        return "돌봄과 관련된 지원제도예요.";
    }

    /** 시설 추천 이유: "{구}에 있는 {시설 유형}이에요." 시설 유형 뒤 괄호 표기는 뗀다 (확정안 4-3) */
    public static String facilityReason(District district, String facilityType) {
        String type = facilityType.replaceAll("\\s*\\(.*\\)\\s*$", "");
        return districtName(district) + "에 있는 " + type + endingFor(type);
    }

    public static String districtName(District district) {
        return switch (district) {
            case CHEOIN -> "처인구";
            case GIHEUNG -> "기흥구";
            case SUJI -> "수지구";
        };
    }

    /** 받침이 없으면 "예요", 있으면 "이에요" */
    private static String endingFor(String word) {
        char last = word.charAt(word.length() - 1);
        boolean hangul = last >= 0xAC00 && last <= 0xD7A3;
        boolean hasBatchim = hangul && (last - 0xAC00) % 28 != 0;
        return hasBatchim ? "이에요." : "예요.";
    }
}
