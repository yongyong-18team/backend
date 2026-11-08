package com.batong.analysis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryMaskerTest {

    @Test
    void 이름은_조사가_붙어도_대상자로_바뀐다() {
        String masked = DiaryMasker.mask("민지가 울었다. 민지는 차에서 진정했고 민지를 안아줬다.", "민지");

        assertThat(masked).isEqualTo("대상자가 울었다. 대상자는 차에서 진정했고 대상자를 안아줬다.");
    }

    @Test
    void 전화번호는_하이픈이_있든_없든_지운다() {
        String masked = DiaryMasker.mask(
                "센터 010-0000-0000, 집 01000000000, 복지관 02-0000-0000, 센터 031-000-0000로 연락함", "민지");

        assertThat(masked).isEqualTo("센터 [연락처], 집 [연락처], 복지관 [연락처], 센터 [연락처]로 연락함");
        assertThat(masked).doesNotContainPattern("\\d{3,}");
    }

    @Test
    void 시간이나_횟수_같은_짧은_숫자는_남긴다() {
        assertThat(DiaryMasker.mask("30분 넘게 소리를 질렀다. 2번 깼다.", "민지"))
                .isEqualTo("30분 넘게 소리를 질렀다. 2번 깼다.");
    }

    @Test
    void 복원하면_대상자가_원래_이름으로_돌아온다() {
        String masked = DiaryMasker.mask("민지가 귀를 막았다", "민지");

        assertThat(DiaryMasker.unmask(masked, "민지")).isEqualTo("민지가 귀를 막았다");
        assertThat(DiaryMasker.unmask("대상자가 30분 넘게 소리를 지름", "가나다")).isEqualTo("가나다가 30분 넘게 소리를 지름");
    }

    @Test
    void 이름이_없는_본문은_그대로다() {
        assertThat(DiaryMasker.mask("오늘은 조용했다.", "민지")).isEqualTo("오늘은 조용했다.");
    }
}
