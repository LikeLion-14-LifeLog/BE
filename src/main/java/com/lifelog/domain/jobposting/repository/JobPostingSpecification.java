package com.lifelog.domain.jobposting.repository;

import com.lifelog.domain.jobposting.entity.JobGroup;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingRegion;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

// null 조건은 쿼리에서 제외 (PostgreSQL에서 null 파라미터 JPQL 타입 추론 문제 회피)
public final class JobPostingSpecification {

    private JobPostingSpecification() {
    }

    // jobGroup이 null이면 그룹 조건 없이 전체 (그룹이 없는 공고 포함), 지정하면 그 그룹만
    public static Specification<JobPosting> search(JobPostingStatus status, JobGroup jobGroup, JobPostingRegion region) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), status));

            if (jobGroup != null) {
                predicates.add(cb.equal(root.get("jobGroup"), jobGroup));
            }
            if (region != null) {
                Expression<String> regionCode = root.get("regionCode");
                List<Predicate> regionPredicates = new ArrayList<>();
                region.getCodePrefixes().forEach(prefix -> regionPredicates.add(cb.like(regionCode, prefix + "%")));
                region.getExactCodes().forEach(code -> regionPredicates.add(cb.equal(regionCode, code)));
                predicates.add(cb.or(regionPredicates.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
