package com.lifelog.domain.job.repository;

import com.lifelog.domain.job.entity.Job;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// null 조건은 쿼리에서 제외 (PostgreSQL에서 null 파라미터 JPQL 타입 추론 문제 회피)
public final class JobSpecification {

    private static final char ESCAPE = '\\';

    private JobSpecification() {
    }

    public static Specification<Job> search(String keyword, String code, String mainCategoryCode,
                                            String middleCategoryCode, String subCategoryCode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
            }
            if (code != null) {
                predicates.add(cb.equal(root.get("code"), code));
            }
            if (mainCategoryCode != null) {
                predicates.add(cb.equal(root.get("mainCategoryCode"), mainCategoryCode));
            }
            if (middleCategoryCode != null) {
                predicates.add(cb.equal(root.get("middleCategoryCode"), middleCategoryCode));
            }
            if (subCategoryCode != null) {
                predicates.add(cb.equal(root.get("subCategoryCode"), subCategoryCode));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // 검색어의 %, _ 를 와일드카드가 아닌 문자 그대로 검색
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
