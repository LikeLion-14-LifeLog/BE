package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpecProfileAnalyzationEventLogRepository extends JpaRepository<SpecProfileAnalyzationEventLog, Long> {
    /** 실행의 전체 상태 및 단계 이력을 발생 순서로 조회한다. @param runId 실행 ID @return 이벤트 이력 */
    List<SpecProfileAnalyzationEventLog> findAllByRun_IdOrderBySortOrderAsc(Long runId);

    /** 실행의 마지막 이벤트를 조회한다. @param runId 실행 ID @return 마지막 이벤트 */
    Optional<SpecProfileAnalyzationEventLog> findFirstByRun_IdOrderBySortOrderDesc(Long runId);

    /** 재접속 커서가 요청 실행에 속하는지 확인한다. @param runId 실행 ID @param id 이벤트 PK @return 소속 여부 */
    boolean existsByRun_IdAndId(Long runId, Long id);
}
