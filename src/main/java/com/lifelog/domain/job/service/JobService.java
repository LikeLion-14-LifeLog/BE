package com.lifelog.domain.job.service;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.dto.JobListResponse;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.exception.JobErrorCode;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.job.repository.JobSpecification;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobCsvReader jobCsvReader;
    private final JobInitService jobInitService;

    // 파일 읽기는 트랜잭션 밖에서 수행하고, upsert만 JobInitService의 트랜잭션으로 처리
    public JobInitResponse initFromCsv() {
        return jobInitService.upsert(jobCsvReader.read());
    }

    @Transactional(readOnly = true)
    public SliceResponse<JobListResponse> search(String keyword, String code, String mainCategoryCode,
                                                 String middleCategoryCode, String subCategoryCode, int page, int size) {
        if (page < 1) {
            throw new CustomException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(CommonErrorCode.INVALID_SIZE);
        }

        // Spring Data 페이지는 0부터 시작
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("code"));
        Page<Job> result = jobRepository.findAll(
                JobSpecification.search(normalizeKeyword(keyword), code, mainCategoryCode,
                        middleCategoryCode, subCategoryCode),
                pageable
        );

        return SliceResponse.of(result, JobListResponse.from(result.getContent()));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String trimmed = keyword.strip();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(JobErrorCode.INVALID_KEYWORD);
        }
        return trimmed;
    }
}
