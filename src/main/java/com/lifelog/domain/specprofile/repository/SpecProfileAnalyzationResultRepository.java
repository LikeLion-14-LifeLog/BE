package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpecProfileAnalyzationResultRepository extends JpaRepository<SpecProfileAnalyzationResult, Long> {
    /**
     * 실행에 저장된 연봉 예측을 연차 오름차순으로 조회한다.
     * @param runId 실행 ID
     * @return 해당 실행의 연차별 결과
     */
    List<SpecProfileAnalyzationResult> findAllByRun_IdOrderByAnnualAsc(Long runId);
}
