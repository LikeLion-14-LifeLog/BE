package com.lifelog.domain.job.service;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.dto.JobListResponse;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.exception.JobErrorCode;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.job.repository.JobSpecification;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobCsvReader jobCsvReader;

    // CSV 기준 code로 upsert: 없으면 생성, 분류·이름이 다르면 수정(jobId 유지), CSV에 없는 기존 직무는 유지
    @Transactional
    public JobInitResponse initFromCsv() {
        List<Job> rows = jobCsvReader.read();

        Map<String, Job> existing = jobRepository.findAll().stream()
                .collect(Collectors.toMap(Job::getCode, Function.identity()));

        List<Job> created = new ArrayList<>();
        int updatedCount = 0;
        int unchangedCount = 0;

        for (Job row : rows) {
            Job job = existing.get(row.getCode());

            if (job == null) {
                created.add(row);
            } else if (job.hasSameValues(row)) {
                unchangedCount++;
            } else {
                job.update(row);
                updatedCount++;
            }
        }
        jobRepository.saveAll(created);

        return new JobInitResponse(rows.size(), created.size(), updatedCount, unchangedCount);
    }

    @Transactional(readOnly = true)
    public JobListResponse search(String keyword, String code, String mainCategoryCode,
                                  String middleCategoryCode, String subCategoryCode, int page, int size) {
        if (page < 1) {
            throw new CustomException(JobErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(JobErrorCode.INVALID_SIZE);
        }

        // Spring Data 페이지는 0부터 시작
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("code"));
        Page<Job> result = jobRepository.findAll(
                JobSpecification.search(normalizeKeyword(keyword), code, mainCategoryCode,
                        middleCategoryCode, subCategoryCode),
                pageable
        );

        return JobListResponse.of(result, page, size);
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
