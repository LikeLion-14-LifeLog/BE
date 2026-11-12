package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 외부 API 호출을 트랜잭션 밖에 두기 위해 저장만 분리 (같은 클래스 내부 호출은 @Transactional 프록시가 적용되지 않음)
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingSyncWriter {

    static final int CHUNK_SIZE = 1000;

    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;

    public record ChunkResult(int createdCount, int updatedCount, int unchangedCount, int closedCount) {
    }

    // externalId 기준 upsert. 모집 중이던 공고가 마감일 경과로 CLOSE가 되면 closed로 집계
    @Transactional
    public ChunkResult upsert(List<JobPostingSyncItem> items) {
        Map<String, Company> companies = resolveCompanies(items);
        Map<String, Job> jobsByCode = resolveJobs(items);
        Map<String, JobPosting> existing = jobPostingRepository
                .findBySourceAndExternalIdIn(SeoulJobPostingMapper.SOURCE,
                        items.stream().map(JobPostingSyncItem::externalId).toList())
                .stream()
                .collect(Collectors.toMap(JobPosting::getExternalId, Function.identity()));

        List<JobPosting> created = new ArrayList<>();
        int updatedCount = 0;
        int unchangedCount = 0;
        int closedCount = 0;

        for (JobPostingSyncItem item : items) {
            JobPosting incoming = item.posting();
            Company company = companies.get(item.companyName());
            // job 테이블에 없는 코드(직무 기준 데이터 미등록 등)면 연결하지 않음
            Job job = item.jobCode() == null ? null : jobsByCode.get(item.jobCode());
            JobPosting posting = existing.get(item.externalId());

            if (posting == null) {
                incoming.assignCompany(company);
                incoming.replaceJob(job);
                created.add(incoming);
            } else if (posting.hasSameValues(incoming)
                    && posting.getCompany().getId().equals(company.getId())
                    && posting.hasOnlyJob(job)) {
                unchangedCount++;
            } else {
                if (posting.isOpen() && !incoming.isOpen()) {
                    closedCount++;
                } else {
                    updatedCount++;
                }
                posting.update(incoming, company);
                posting.replaceJob(job);
            }
        }
        jobPostingRepository.saveAllAndFlush(created);

        return new ChunkResult(created.size(), updatedCount, unchangedCount, closedCount);
    }

    // 이번 원본에 없는 모집 중 공고를 CLOSE로 변경
    @Transactional
    public int closeMissing(Set<String> fetchedExternalIds) {
        List<String> missing = jobPostingRepository
                .findExternalIdsBySourceAndStatus(SeoulJobPostingMapper.SOURCE, JobPostingStatus.OPEN)
                .stream()
                .filter(id -> !fetchedExternalIds.contains(id))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        int closedCount = 0;
        for (int from = 0; from < missing.size(); from += CHUNK_SIZE) {
            List<String> chunk = missing.subList(from, Math.min(from + CHUNK_SIZE, missing.size()));
            closedCount += jobPostingRepository.updateStatus(
                    SeoulJobPostingMapper.SOURCE, chunk, JobPostingStatus.CLOSE, now);
        }
        return closedCount;
    }

    private Map<String, Job> resolveJobs(List<JobPostingSyncItem> items) {
        Set<String> codes = new HashSet<>();
        items.forEach(item -> {
            if (item.jobCode() != null) {
                codes.add(item.jobCode());
            }
        });
        Map<String, Job> jobs = new HashMap<>();
        if (!codes.isEmpty()) {
            jobRepository.findByCodeIn(codes).forEach(job -> jobs.put(job.getCode(), job));
        }
        if (jobs.size() < codes.size()) {
            log.warn("[JobPostingSyncWriter] job 테이블에 없는 직무 코드가 있어 직무 연결을 건너뜀 | missing: {}",
                    codes.stream().filter(code -> !jobs.containsKey(code)).sorted().toList());
        }
        return jobs;
    }

    // 기업은 고유 키가 없어 이름으로 조회 (같은 이름이 여러 건이면 먼저 생성된 기업), 없으면 생성
    private Map<String, Company> resolveCompanies(List<JobPostingSyncItem> items) {
        Set<String> names = new HashSet<>();
        items.forEach(item -> names.add(item.companyName()));

        Map<String, Company> companies = new HashMap<>();
        companyRepository.findByNameInOrderByIdAsc(names)
                .forEach(company -> companies.putIfAbsent(company.getName(), company));

        List<Company> newCompanies = new ArrayList<>();
        for (JobPostingSyncItem item : items) {
            Company company = companies.get(item.companyName());
            if (company == null) {
                company = Company.builder()
                        .name(item.companyName())
                        .industryName(item.industryName())
                        .build();
                companies.put(item.companyName(), company);
                newCompanies.add(company);
            } else {
                company.fillIndustryNameIfAbsent(item.industryName());
            }
        }
        companyRepository.saveAll(newCompanies);
        return companies;
    }
}
