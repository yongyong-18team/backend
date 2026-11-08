package com.batong.infra.publicdata;

import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacilitySeedReaderTest {

    private static final String HEADER =
        "source,external_id,resource_kind,name,phone,official_url,facility_type,district,address,lat,lng,services,fetched_at\n";

    @Test
    void 실제_시드_CSV를_모두_읽는다() throws IOException {
        try (Reader reader = new InputStreamReader(
            new ClassPathResource("publicdata/facility_seed_yongin.csv").getInputStream(), StandardCharsets.UTF_8)) {
            List<FacilitySeedRow> rows = FacilitySeedReader.read(reader);

            assertThat(rows).hasSize(53);
            assertThat(rows).filteredOn(r -> r.source() == FacilitySource.YONGIN_API).hasSize(42);
            assertThat(rows).filteredOn(r -> r.source() == FacilitySource.MANUAL).hasSize(11);
        }
    }

    @Test
    void 빈_값은_null로_바꾸고_날짜는_Instant로_바꾼다() throws IOException {
        String csv = HEADER + "MANUAL,MANUAL-01,FACILITY,마실장애인지원센터,,,주간이용센터,GIHEUNG,,,,,2026-11-08T17:00:00+09:00\n";

        FacilitySeedRow row = FacilitySeedReader.read(new StringReader(csv)).get(0);

        assertThat(row.district()).isEqualTo(District.GIHEUNG);
        assertThat(row.phone()).isNull();
        assertThat(row.lat()).isNull();
        assertThat(row.fetchedAt().toString()).isEqualTo("2026-11-08T08:00:00Z");
    }

    @Test
    void 잘못된_구_값은_몇_번째_행인지_알려준다() {
        String csv = HEADER + "MANUAL,MANUAL-01,FACILITY,가,,,주간이용센터,XX,,,,,2026-11-08T17:00:00+09:00\n";

        assertThatThrownBy(() -> FacilitySeedReader.read(new StringReader(csv)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1번째 행");
    }

    @Test
    void 필수_값이_비면_예외다() {
        String csv = HEADER + "MANUAL,MANUAL-01,FACILITY,,,,주간이용센터,SUJI,,,,,2026-11-08T17:00:00+09:00\n";

        assertThatThrownBy(() -> FacilitySeedReader.read(new StringReader(csv)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void source와_external_id가_중복이면_예외다() {
        String row = "MANUAL,MANUAL-01,FACILITY,가,,,주간이용센터,SUJI,,,,,2026-11-08T17:00:00+09:00\n";

        assertThatThrownBy(() -> FacilitySeedReader.read(new StringReader(HEADER + row + row)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("중복");
    }
}
