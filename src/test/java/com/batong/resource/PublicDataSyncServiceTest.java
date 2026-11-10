package com.batong.resource;

import com.batong.TestcontainersConfiguration;
import com.batong.resource.dto.PublicDataDtos.SyncResponse;
import com.batong.resource.repository.FacilityRepository;
import com.batong.resource.service.PublicDataSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PublicDataSyncService.class})
class PublicDataSyncServiceTest {

    @Autowired
    PublicDataSyncService service;

    @Autowired
    FacilityRepository facilityRepository;

    @Test
    void 처음_적재하면_시드_전체를_새로_넣는다() {
        SyncResponse result = service.sync();

        assertThat(result).isEqualTo(new SyncResponse(53, 53, 0));
        assertThat(facilityRepository.count()).isEqualTo(53);
    }

    @Test
    void 다시_적재해도_중복으로_쌓이지_않는다() {
        service.sync();

        SyncResponse again = service.sync();

        assertThat(again).isEqualTo(new SyncResponse(53, 0, 53));
        assertThat(facilityRepository.count()).isEqualTo(53);
    }
}
