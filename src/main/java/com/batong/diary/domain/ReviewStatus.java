package com.batong.diary.domain;

/**
 * DIARY-03·04 응답의 reviewStatus. 저장하지 않는 계산값이다(ERD: extracted_item에 PENDING이 남아 있으면 검토 대기).
 * 공용 enum이 아니라 임시로 여기에 둔다. 백엔드 A가 global/enums에 추가하면 옮긴다
 */
public enum ReviewStatus {
    /** 분석 전 */
    NONE,
    /** PENDING 항목이 있음 */
    PENDING,
    /** PENDING 항목이 없음 */
    APPROVED
}
