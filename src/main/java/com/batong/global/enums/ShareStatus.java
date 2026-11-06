package com.batong.global.enums;

/** 공유 상태. 저장하지 않고 approved_at·revoked_at·expires_at으로 계산한다(ERD 3-3). DRAFT는 목록에 나오지 않는다. */
public enum ShareStatus {
    DRAFT,
    ACTIVE,
    EXPIRED,
    REVOKED
}
