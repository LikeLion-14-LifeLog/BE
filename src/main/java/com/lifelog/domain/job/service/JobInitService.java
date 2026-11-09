package com.lifelog.domain.job.service;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// CSV 읽기를 트랜잭션 밖에 두기 위해 분리 (같은 클래스 내부 호출은 @Transactional 프록시가 적용되지 않음)
@Service
@RequiredArgsConstructor
public class JobInitService {

    private final JobRepository jobRepository;

    // CSV 기준 code로 upsert: 없으면 생성, 분류·이름이 다르면 수정(jobId 유지), CSV에 없는 기존 직무는 유지
    @Transactional
    public JobInitResponse upsert(List<Job> rows) {
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
}
