package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnnualSalaryResultSaveService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationResultRepository resultRepository;
    private final SpecProfileAnnualSalaryAnalyzationMapper mapper;

    /**
     * 연차별 예측, AI 진단 문구와 완료 상태를 하나의 독립 트랜잭션으로 저장한다.
     * @param runId 실행 ID
     * @param report 검증된 전체 분석 결과
     * @return 저장과 완료를 수행했는지 여부. 진행 중 상태가 아니면 변경하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveAndComplete(Long runId, SpecProfileAnnualSalaryAnalyzationResDto report) {
        log.info("[SpecProfileAnnualSalaryResultSaveService] 결과 저장 | saveAndComplete() - START | runId: {}", runId);
        /* (1) 실행 행을 잠그고 진행 중 상태인지 확인한다. 중복 완료와 취소 후 저장을 방지한다. */
        SpecProfileAnalyzationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        if (run.getStatus() != SpecProfileAnalyzationStatus.IN_PROGRESS) {
            log.info("[SpecProfileAnnualSalaryResultSaveService] 종료 또는 대기 실행 생략 | saveAndComplete() - END | runId: {}, status: {}",
                    runId, run.getStatus());
            return false;
        }
        /* (2) 연차별 예측과 AI가 생성한 요약·비교 집단 설명·보완점을 컬럼에 저장한다. */
        resultRepository.saveAllAndFlush(mapper.toResults(run, report));
        /* (3) 결과와 COMPLETED를 함께 반영한다. 커밋 실패도 호출자에게 전파해 FAILED로 보상한다. */
        run.completeAnnualSalaryAnalyzation();
        runRepository.flush();
        log.info("[SpecProfileAnnualSalaryResultSaveService] 결과 저장 | saveAndComplete() - END | runId: {}, count: {}",
                runId, report.salaryResults().size());
        return true;
    }

}
