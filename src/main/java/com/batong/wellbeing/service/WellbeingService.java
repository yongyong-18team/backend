package com.batong.wellbeing.service;

import com.batong.global.enums.SignalType;
import org.springframework.stereotype.Service;

@Service
public class WellbeingService {

    /**
     * 소진·위기 신호를 저장하고, 소진 안내를 띄울지 판정한다. (AI 명세 3-6)
     *
     * 호출: DIARY-02 분석 트랜잭션(TRD 8-3 Tx2) 안에서 호출한다. 이 메서드는 트랜잭션을 새로 열지 않는다.
     *
     * - BURNOUT: wellbeing_signal(user, BURNOUT, 오늘 KST) 저장. 같은 날 이미 있으면 무시한다.
     *   최근 14일(오늘 포함) 중 BURNOUT 기록 날짜가 3일 이상이고
     *   users.last_burnout_notice_at이 NULL이거나 14일 이전이면 true를 반환하고
     *   last_burnout_notice_at을 now()로 갱신한다.
     * - CRISIS: wellbeing_signal(user, CRISIS, 오늘 KST) 저장(같은 날 중복 무시). 누적 판정에는 넣지 않고 항상 false.
     *
     * @param userId  주 보호자 users.user_id
     * @param diaryId 신호가 나온 일기. 일기가 삭제되면 NULL이 될 수 있다.
     * @param type    BURNOUT 또는 CRISIS
     * @return DIARY-02 응답의 burnoutNotice 값
     */
    public boolean record(Long userId, Long diaryId, SignalType type) {
        // 스텁: 저장·누적 판정은 이후 PR에서 구현한다.
        return false;
    }
}
