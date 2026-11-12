package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpecProfileAnalyzationRunRepository extends JpaRepository<SpecProfileAnalyzationRun, Long> {
    /**
     * 프로필의 최신 실행을 요청 시각과 PK 역순으로 조회한다.
     * @param specProfileId 잠금 상태인 스펙 프로필 ID
     * @return 최신 실행 또는 빈 값
     */
    Optional<SpecProfileAnalyzationRun> findFirstBySpecProfileIdOrderByCreatedAtDescIdDesc(Long specProfileId);
}
