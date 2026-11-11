package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.jobposting.dto.JobPostingJobCategoryListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingJobCategoryResponse;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 공고 목록 직무 필터(jobCategory)에 쓸 소분류를 검색 (검색형 선택 UI용)
@Service
@RequiredArgsConstructor
public class JobPostingCategoryService {

    private static final int MAX_KEYWORD_LENGTH = 50;

    private final JobRepository jobRepository;

    /**
     * 직무 기준 데이터(한국고용직업분류 2025)의 소분류를 검색한다. 미분류처럼 소분류가 없는 직무는 제외한다.
     * 검색어가 소분류명 또는 그 아래 세분류명에 포함되면(대소문자 무시) 해당 소분류를 반환하고, 없으면 전체를 반환한다.
     * 직무 기준 데이터가 수백 건이라 전체를 읽어 메모리에서 묶는다.
     */
    @Transactional(readOnly = true)
    public JobPostingJobCategoryListResponse search(String keyword) {
        String lowered = normalizeKeyword(keyword);

        Map<String, List<Job>> bySubCategory = new LinkedHashMap<>();
        for (Job job : jobRepository.findAll(Sort.by("code"))) {
            if (job.getSubCategoryCode() != null) {
                bySubCategory.computeIfAbsent(job.getSubCategoryCode(), code -> new ArrayList<>()).add(job);
            }
        }

        List<JobPostingJobCategoryResponse> categories = bySubCategory.values().stream()
                .filter(jobs -> lowered == null || matches(jobs, lowered))
                .map(JobPostingJobCategoryResponse::from)
                .toList();
        return new JobPostingJobCategoryListResponse(categories);
    }

    private boolean matches(List<Job> jobs, String loweredKeyword) {
        if (jobs.getFirst().getSubCategoryName().toLowerCase(Locale.ROOT).contains(loweredKeyword)) {
            return true;
        }
        return jobs.stream().anyMatch(job -> job.getName().toLowerCase(Locale.ROOT).contains(loweredKeyword));
    }

    // 앞뒤 공백 제거 후 최대 50자, 비어 있으면 null
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.strip();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(JobPostingErrorCode.INVALID_KEYWORD);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
