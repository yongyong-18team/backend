package com.batong.resource.repository;

import com.batong.global.enums.ProgramType;
import com.batong.resource.domain.SupportProgram;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportProgramRepository extends JpaRepository<SupportProgram, Long> {

    /** 제도는 5개뿐이라 전부 읽어 서비스에서 거른다. 관련 시설 유형은 한 번에 같이 가져온다 */
    @EntityGraph(attributePaths = "facilityTypes")
    List<SupportProgram> findAllByOrderByIdAsc();

    /** 소진·위기 안내가 보여줄 제도를 유형으로 찾는다. 시설 유형은 필요 없어서 따로 가져오지 않는다 */
    List<SupportProgram> findByProgramTypeIn(Collection<ProgramType> programTypes);
}
