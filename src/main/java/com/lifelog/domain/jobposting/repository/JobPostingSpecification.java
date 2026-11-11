package com.lifelog.domain.jobposting.repository;

import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.JobGroup;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingJob;
import com.lifelog.domain.jobposting.entity.JobPostingRegion;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.Builder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// null 조건은 쿼리에서 제외 (PostgreSQL에서 null 파라미터 JPQL 타입 추론 문제 회피)
public final class JobPostingSpecification {

    private JobPostingSpecification() {
    }

    /**
     * 목록 조회 조건.
     * @param today              마감일이 지난 공고 제외 기준 (closes_at IS NULL OR closes_at >= 오늘 0시)
     * @param jobGroup           null이면 그룹 조건 없이 전체 (그룹이 없는 공고 포함)
     * @param region             null이면 전체
     * @param jobIds             맞춤: 이 직무 중 하나에 연결된 공고만 (null이면 조건 없음)
     * @param maxEducationLevel  맞춤: 공고 최소 학력이 이 단계 이하인 공고만 (null이면 조건 없음)
     * @param newOrAnyOnly       맞춤: 신입·경력무관 공고만
     */
    @Builder
    public record Condition(JobPostingStatus status, LocalDate today, JobGroup jobGroup, JobPostingRegion region,
                            Set<Long> jobIds, Integer maxEducationLevel, boolean newOrAnyOnly) {
    }

    public static Specification<JobPosting> search(Condition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), condition.status()));

            Expression<LocalDateTime> closesAt = root.get("closesAt");
            predicates.add(cb.or(
                    cb.isNull(closesAt),
                    cb.greaterThanOrEqualTo(closesAt, condition.today().atStartOfDay())));

            if (condition.jobGroup() != null) {
                predicates.add(cb.equal(root.get("jobGroup"), condition.jobGroup()));
            }
            if (condition.region() != null) {
                Expression<String> regionCode = root.get("regionCode");
                List<Predicate> regionPredicates = new ArrayList<>();
                condition.region().getCodePrefixes()
                        .forEach(prefix -> regionPredicates.add(cb.like(regionCode, prefix + "%")));
                condition.region().getExactCodes()
                        .forEach(code -> regionPredicates.add(cb.equal(regionCode, code)));
                predicates.add(cb.or(regionPredicates.toArray(Predicate[]::new)));
            }
            if (condition.jobIds() != null) {
                // 직무 연결 테이블을 조인하면 행이 늘어나므로 EXISTS 서브쿼리로 판단
                Subquery<Long> sub = query.subquery(Long.class);
                Root<JobPostingJob> link = sub.from(JobPostingJob.class);
                sub.select(link.get("id")).where(
                        cb.equal(link.get("jobPosting"), root),
                        link.get("job").get("id").in(condition.jobIds()));
                predicates.add(cb.exists(sub));
            }
            if (condition.maxEducationLevel() != null) {
                predicates.add(cb.le(root.get("educationLevel"), condition.maxEducationLevel().shortValue()));
            }
            if (condition.newOrAnyOnly()) {
                predicates.add(root.get("careerType").in(CareerType.NEW, CareerType.ANY));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
