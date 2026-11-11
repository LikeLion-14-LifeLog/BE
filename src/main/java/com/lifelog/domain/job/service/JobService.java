package com.lifelog.domain.job.service;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.dto.JobListResponse;
import com.lifelog.domain.job.dto.JobSubCategoryListResponse;
import com.lifelog.domain.job.dto.JobSubCategoryResponse;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * 직무 소분류 검색 (검색형 선택 UI용). 미분류처럼 소분류가 없는 직무는 제외한다.
     * 검색어가 소분류명 또는 그 아래 세분류명에 포함되면(대소문자 무시) 해당 소분류를 반환하고, 없으면 전체를 반환한다.
     * 직무 기준 데이터가 수백 건이라 전체를 읽어 메모리에서 묶는다.
     */
    @Transactional(readOnly = true)
    public JobSubCategoryListResponse searchSubCategories(String keyword) {
        String normalized = normalizeKeyword(keyword);
        String lowered = normalized == null ? null : normalized.toLowerCase(Locale.ROOT);

        Map<String, List<Job>> bySubCategory = new LinkedHashMap<>();
        for (Job job : jobRepository.findAll(Sort.by("code"))) {
            if (job.getSubCategoryCode() != null) {
                bySubCategory.computeIfAbsent(job.getSubCategoryCode(), code -> new ArrayList<>()).add(job);
            }
        }

        List<JobSubCategoryResponse> subCategories = bySubCategory.values().stream()
                .filter(jobs -> lowered == null || matches(jobs, lowered))
                .map(JobSubCategoryResponse::from)
                .toList();
        return new JobSubCategoryListResponse(subCategories);
    }

    private boolean matches(List<Job> jobs, String loweredKeyword) {
        if (jobs.getFirst().getSubCategoryName().toLowerCase(Locale.ROOT).contains(loweredKeyword)) {
            return true;
        }
        return jobs.stream().anyMatch(job -> job.getName().toLowerCase(Locale.ROOT).contains(loweredKeyword));
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
