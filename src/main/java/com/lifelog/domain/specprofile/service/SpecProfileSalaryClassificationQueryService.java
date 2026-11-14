package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileSalaryClassificationMapper;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileSalaryClassificationQueryService {
    private final JobRepository jobRepository;
    private final SpecProfileSalaryClassificationMapper mapper;

    /**
     * 독립 읽기 트랜잭션에서 직무 소분류와 스냅샷의 항목별 분류 조건을 조회한다.
     * @param snapshot 요청 시점 스펙
     * @return 구간 계산 전 분류 조건
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public SpecProfileSalaryClassificationData getClassificationData(SpecProfileAnalyzationSnapshot snapshot) {
        log.info("[SpecProfileSalaryClassificationQueryService] 조건 조회 | getClassificationData() - START");
        if (snapshot.jobId() == null || snapshot.academicAbilities() == null
                || snapshot.languageExamScores() == null || snapshot.certificates() == null) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT);
        }
        Job job = jobRepository.findById(snapshot.jobId())
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST));
        if (job.getSubCategoryCode() == null || job.getSubCategoryCode().isBlank()) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST);
        }
        SpecProfileSalaryClassificationData result = mapper.toClassificationData(job, snapshot);
        log.info("[SpecProfileSalaryClassificationQueryService] 조건 조회 | getClassificationData() - END");
        return result;
    }
}
