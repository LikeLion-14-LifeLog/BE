package com.lifelog.domain.specprofile.service.specprofiletarget;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationResultQueryService;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileTargetQueryService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationResultQueryService resultQueryService;
    private final SpecProfileTargetMapper mapper;
    private final JsonMapper jsonMapper;

    /**
     * 현재 스냅샷과 일치하는 최신 완료 진단을 조회하며 없는 결과는 저장하지 않는다.
     * @param memberId 인증 회원
     * @param base 요청 CURRENT
     * @return 기존 진단 결과. 없거나 낡은 진단이면 null
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileAnnualSalaryAnalyzationResDto findStoredCurrentAnalysis(Long memberId, SpecProfileAnalyzationSnapshot base) {
        log.info("[SpecProfileTargetQueryService] 기존 CURRENT 진단 조회 | findStoredCurrentAnalysis() - START | profileId: {}", base.specProfileId());
        SpecProfileAnalyzationRun run = runRepository.findFirstBySpecProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                base.specProfileId(), SpecProfileAnalyzationStatus.COMPLETED).orElse(null);
        SpecProfileAnnualSalaryAnalyzationResDto result = null;
        if (run != null) {
            try {
                SpecProfileAnalyzationSnapshot saved = jsonMapper.readValue(run.getSpecProfileSnapshotJson(), SpecProfileAnalyzationSnapshot.class);
                if (mapper.comparisonKey(base).equals(mapper.comparisonKey(saved))) {
                    result = resultQueryService.getRunResult(memberId, run.getId()).result();
                }
            } catch (JacksonException exception) {
                log.warn("[SpecProfileTargetQueryService] 기존 진단 스냅샷 해석 실패 | runId: {}", run.getId());
            } catch (CustomException exception) {
                if (exception.getErrorCode() != SpecProfileAnalyzationErrorCode.INCOMPLETE_SAVED_RESULT
                        && exception.getErrorCode() != SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT) throw exception;
                log.warn("[SpecProfileTargetQueryService] 불완전한 기존 진단 제외 | runId: {}", run.getId());
            }
        }
        log.info("[SpecProfileTargetQueryService] 기존 CURRENT 진단 조회 | findStoredCurrentAnalysis() - END | found: {}", result != null);
        return result;
    }
}
