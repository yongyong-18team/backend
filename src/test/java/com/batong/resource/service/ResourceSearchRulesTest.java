package com.batong.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceSearchRulesTest {

    private static final Map<String, Object> EMERGENCY = Map.of("minAge", 6, "maxAge", 65);
    private static final Map<String, Object> DAY_ACTIVITY = Map.of("minAge", 18);
    private static final Map<String, Object> NO_LIMIT = Map.of();

    @Test
    void careTerm이_없으면_모든_제도가_맞다() {
        assertThat(ResourceSearchRules.matchesCareTerm(CareTerm.SHORT, null)).isTrue();
        assertThat(ResourceSearchRules.matchesCareTerm(CareTerm.LONG, null)).isTrue();
    }

    @Test
    void careTerm이_같거나_BOTH인_제도만_맞다() {
        assertThat(ResourceSearchRules.matchesCareTerm(CareTerm.SHORT, CareTerm.SHORT)).isTrue();
        assertThat(ResourceSearchRules.matchesCareTerm(CareTerm.SHORT, CareTerm.LONG)).isFalse();
        assertThat(ResourceSearchRules.matchesCareTerm(CareTerm.BOTH, CareTerm.SHORT)).isTrue();
        assertThat(ResourceSearchRules.matchesCareTerm(CareTerm.BOTH, CareTerm.LONG)).isTrue();
    }

    @Test
    void 연령_범위_안이면_맞다() {
        assertThat(ResourceSearchRules.matchesAge(EMERGENCY, 30)).isTrue();
    }

    @Test
    void 최소_연령_경계는_모호하면_포함한다() {
        // 연 나이 6은 만 5세일 수도 6세일 수도 있다
        assertThat(ResourceSearchRules.matchesAge(EMERGENCY, 6)).isTrue();
        // 연 나이 5는 만 4~5세라 6세 미만이 확실하다
        assertThat(ResourceSearchRules.matchesAge(EMERGENCY, 5)).isFalse();
    }

    @Test
    void 최대_연령은_미만이고_경계는_모호하면_포함한다() {
        // 연 나이 65는 만 64세일 수도 있어서 65세 미만에 해당할 수 있다
        assertThat(ResourceSearchRules.matchesAge(EMERGENCY, 65)).isTrue();
        // 연 나이 66은 만 65~66세라 65세 미만이 아니다
        assertThat(ResourceSearchRules.matchesAge(EMERGENCY, 66)).isFalse();
    }

    @Test
    void 최소_연령만_있으면_상한이_없다() {
        assertThat(ResourceSearchRules.matchesAge(DAY_ACTIVITY, 17)).isFalse();
        assertThat(ResourceSearchRules.matchesAge(DAY_ACTIVITY, 18)).isTrue();
        assertThat(ResourceSearchRules.matchesAge(DAY_ACTIVITY, 90)).isTrue();
    }

    @Test
    void 조건이_비어_있으면_나이와_상관없이_맞다() {
        assertThat(ResourceSearchRules.matchesAge(NO_LIMIT, 0)).isTrue();
        assertThat(ResourceSearchRules.matchesAge(NO_LIMIT, 99)).isTrue();
    }

    @Test
    void 출생_연도가_올해면_나이_0도_계산이_깨지지_않는다() {
        assertThat(ResourceSearchRules.matchesAge(Map.of("maxAge", 3), 0)).isTrue();
    }

    @Test
    void 조건_값이_숫자가_아니면_시드_오류로_알린다() {
        assertThatThrownBy(() -> ResourceSearchRules.matchesAge(Map.of("minAge", "6"), 10))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 제도_추천_이유는_careTerm에_따른다() {
        assertThat(ResourceSearchRules.programReason(CareTerm.SHORT)).isEqualTo("단기 돌봄과 관련된 제도예요.");
        assertThat(ResourceSearchRules.programReason(CareTerm.LONG)).isEqualTo("장기 돌봄과 관련된 제도예요.");
        assertThat(ResourceSearchRules.programReason(null)).isEqualTo("돌봄과 관련된 지원제도예요.");
    }

    @Test
    void 시설_추천_이유는_받침에_따라_예요_이에요를_고른다() {
        assertThat(ResourceSearchRules.facilityReason(District.SUJI, "주간이용센터"))
            .isEqualTo("수지구에 있는 주간이용센터예요.");
        assertThat(ResourceSearchRules.facilityReason(District.GIHEUNG, "장애인복지관"))
            .isEqualTo("기흥구에 있는 장애인복지관이에요.");
        assertThat(ResourceSearchRules.facilityReason(District.CHEOIN, "장애아재활치료시설"))
            .isEqualTo("처인구에 있는 장애아재활치료시설이에요.");
    }

    @Test
    void 시설_유형_뒤_괄호_표기는_뗀다() {
        assertThat(ResourceSearchRules.facilityReason(District.CHEOIN, "자립생활지원센터(IL)"))
            .isEqualTo("처인구에 있는 자립생활지원센터예요.");
    }
}
