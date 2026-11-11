package com.lifelog.domain.jobposting.repository;

import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingJob;
import com.lifelog.domain.jobposting.entity.JobPostingRegion;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
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
import java.util.function.Function;

// null 조건은 쿼리에서 제외 (PostgreSQL에서 null 파라미터 JPQL 타입 추론 문제 회피)
public final class JobPostingSpecification {

    private JobPostingSpecification() {
    }

    /**
     * 목록 조회 조건. 직무 조건은 공고에 연결된 직무(job_posting_job → job)의 분류로 판단한다.
     * @param today              마감일이 지난 공고 제외 기준 (closes_at IS NULL OR closes_at >= 오늘 0시)
     * @param jobCategory        중분류 코드 2자리 (예: "13"), null이면 전체
     * @param region             null이면 전체
     * @param subCategoryCodes   맞춤: 연결된 직무의 소분류(3자리)가 이 중 하나인 공고만 (null이면 조건 없음)
     * @param maxEducationLevel  맞춤: 공고 최소 학력이 이 단계 이하인 공고만 (null이면 조건 없음)
     * @param newOrAnyOnly       맞춤: 신입·경력무관 공고만
     */
    @Builder(toBuilder = true)
    public record Condition(JobPostingStatus status, LocalDate today, String jobCategory, JobPostingRegion region,
                            Set<String> subCategoryCodes, Integer maxEducationLevel, boolean newOrAnyOnly) {
    }

    public static Specification<JobPosting> search(Condition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), condition.status()));

            Expression<LocalDateTime> closesAt = root.get("closesAt");
            predicates.add(cb.or(
                    cb.isNull(closesAt),
                    cb.greaterThanOrEqualTo(closesAt, condition.today().atStartOfDay())));

            if (condition.jobCategory() != null) {
                predicates.add(linkedJobExists(root, query, cb,
                        job -> cb.equal(job.get("middleCategoryCode"), condition.jobCategory())));
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
            if (condition.subCategoryCodes() != null) {
                predicates.add(linkedJobExists(root, query, cb,
                        job -> job.get("subCategoryCode").in(condition.subCategoryCodes())));
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

    // 직무 연결 테이블을 조인하면 행이 늘어나므로 EXISTS 서브쿼리로 판단
    private static Predicate linkedJobExists(Root<JobPosting> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                             Function<Path<?>, Predicate> jobCondition) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<JobPostingJob> link = sub.from(JobPostingJob.class);
        sub.select(link.get("id")).where(
                cb.equal(link.get("jobPosting"), root),
                jobCondition.apply(link.get("job")));
        return cb.exists(sub);
    }
}
