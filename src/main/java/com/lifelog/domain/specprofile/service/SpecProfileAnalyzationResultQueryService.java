package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationResult;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnnualSalaryAnalyzationMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationResultRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationResultQueryService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationResultRepository resultRepository;
    private final SpecProfileAnnualSalaryAnalyzationMapper mapper;
    private final JsonMapper jsonMapper;

    /**
     * 본인 실행의 상태와 저장된 보고서를 읽기 트랜잭션 안에서 구성한다.
     * @param memberId 인증 회원 ID
     * @param runId 조회할 실행 ID
     * @return 실행 상태와 완료 시점의 결과. 미완료 실행의 결과는 null
     */
    @Transactional(readOnly = true)
    public SpecProfileAnalyzationRunResultResDto getRunResult(Long memberId, Long runId) {
        log.info("[SpecProfileAnalyzationResultQueryService] 실행 결과 조회 | getRunResult() - START | memberId: {}, runId: {}", memberId, runId);
        /* (1) 요청한 실행의 소유권을 확인한다. 타인 실행과 미존재 실행은 동일하게 처리한다. */
        SpecProfileAnalyzationRun run = runRepository.findOwnedRun(runId, memberId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        /* (2) 완료된 실행만 저장된 연차별 결과와 요청 시점 스냅샷으로 복원한다. */
        SpecProfileAnnualSalaryAnalyzationResDto report = null;
        if (run.getStatus() == SpecProfileAnalyzationStatus.COMPLETED) {
            List<SpecProfileAnalyzationResult> results = resultRepository.findAllByRun_IdOrderByAnnualAsc(runId);
            validateStoredResults(results);
            report = mapper.toResDto(results, readSnapshot(run));
        }
        /* (3) 지연 로딩 컬렉션까지 DTO로 복사해 트랜잭션 밖에서 Entity에 접근하지 않게 한다. */
        SpecProfileAnalyzationRunResultResDto result = mapper.toRunResultResDto(run, report);
        log.info("[SpecProfileAnalyzationResultQueryService] 실행 결과 조회 | getRunResult() - END | runId: {}, status: {}", runId, run.getStatus());
        return result;
    }

    /**
     * 과거 미저장 통계나 누락된 연차를 임의 값으로 보완하지 않고 거절한다.
     * @param results 연차 오름차순의 저장 결과
     */
    private void validateStoredResults(List<SpecProfileAnalyzationResult> results) {
        log.debug("[SpecProfileAnalyzationResultQueryService] 저장 결과 검증 | validateStoredResults() - START");
        if (!results.stream().map(SpecProfileAnalyzationResult::getAnnual).toList().equals(List.of(0, 3, 5))) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INCOMPLETE_SAVED_RESULT);
        }
        SpecProfileAnalyzationResult common = results.getFirst();
        if (common.getSampleCount() == null || common.getModeFrequency() == null
                || common.getAnnualSalaryMinKrw() == null || common.getAnnualSalaryMaxKrw() == null
                || common.getAnnualSalaryMeanKrw() == null || common.getAnnualSalaryP25Krw() == null
                || common.getAnnualSalaryMedianKrw() == null || common.getAnnualSalaryP75Krw() == null
                || common.getJobSubCategoryCode() == null || common.getJobSubCategoryName() == null
                || common.getSummary() == null || common.getComparisonGroupDescription() == null
                || common.getRecommendation() == null || common.getSalaryNotice() == null
                || common.getTopPercent() == null || common.getObservationYears().isEmpty()) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INCOMPLETE_SAVED_RESULT);
        }
        log.debug("[SpecProfileAnalyzationResultQueryService] 저장 결과 검증 | validateStoredResults() - END");
    }

    /**
     * 현재 스펙을 조회하지 않고 실행에 보관된 스냅샷을 읽는다.
     * @param run 본인 소유의 실행
     * @return 요청 당시 스냅샷
     */
    private SpecProfileAnalyzationSnapshot readSnapshot(SpecProfileAnalyzationRun run) {
        log.debug("[SpecProfileAnalyzationResultQueryService] 스냅샷 복원 | readSnapshot() - START | runId: {}", run.getId());
        try {
            SpecProfileAnalyzationSnapshot snapshot = jsonMapper.readValue(run.getSpecProfileSnapshotJson(), SpecProfileAnalyzationSnapshot.class);
            if (snapshot == null) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT);
            }
            log.debug("[SpecProfileAnalyzationResultQueryService] 스냅샷 복원 | readSnapshot() - END | runId: {}", run.getId());
            return snapshot;
        } catch (JacksonException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT);
        }
    }
}
