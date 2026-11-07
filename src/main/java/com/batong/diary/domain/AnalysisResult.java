package com.batong.diary.domain;

/**
 * DIARY-02 응답의 result. 저장하지 않는 계산값이다.
 * 공용 enum이 아니라 임시로 여기에 둔다. 백엔드 A가 global/enums에 추가하면 옮긴다
 */
public enum AnalysisResult {
    /** 검토할 항목 있음 → 2.3 */
    REVIEW,
    /** 추출 결과 없음 안내 */
    NO_ITEMS,
    /** 일기는 저장됨, 5.5로 바로 이동 */
    CRISIS
}
