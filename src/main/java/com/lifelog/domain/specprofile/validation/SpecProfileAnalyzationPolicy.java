package com.lifelog.domain.specprofile.validation;

import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpecProfileAnalyzationPolicy {
    private final SpecProfileAnalyzationRunRepository runRepository;

    /**
     * 잠금 상태인 프로필의 최신 실행이 대기 또는 진행 중이면 요청을 거절한다.
     * @param specProfileId CURRENT 잠금을 획득한 프로필 ID
     */
    public void validateLatestRunNotRunning(Long specProfileId) {
        runRepository.findFirstBySpecProfileIdOrderByCreatedAtDescIdDesc(specProfileId)
                .filter(run -> run.getStatus().isRunning())
                .ifPresent(run -> {
                    throw new CustomException(SpecProfileAnalyzationErrorCode.ANALYSIS_ALREADY_RUNNING);
                });
    }
}
