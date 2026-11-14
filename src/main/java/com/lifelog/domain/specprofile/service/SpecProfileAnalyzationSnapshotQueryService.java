package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationSnapshotQueryService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final JsonMapper jsonMapper;

    /**
     * 저장된 요청 시점 스냅샷을 별도 읽기 트랜잭션에서 역직렬화한다.
     * @param runId 실행 ID
     * @return 저장된 스냅샷 DTO
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public SpecProfileAnalyzationSnapshot getSnapshot(Long runId) {
        log.info("[SpecProfileAnalyzationSnapshotQueryService] 스냅샷 조회 | getSnapshot() - START | runId: {}", runId);
        String json = runRepository.findSnapshotJsonById(runId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        try {
            SpecProfileAnalyzationSnapshot snapshot = jsonMapper.readValue(json, SpecProfileAnalyzationSnapshot.class);
            if (snapshot == null) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT);
            }
            log.info("[SpecProfileAnalyzationSnapshotQueryService] 스냅샷 조회 | getSnapshot() - END | runId: {}", runId);
            return snapshot;
        } catch (JacksonException exception) {
            log.error("[SpecProfileAnalyzationSnapshotQueryService] 스냅샷 역직렬화 실패 | runId: {}", runId);
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT);
        }
    }
}
