package com.lifelog.domain.jobposting.entity;

import org.springframework.data.domain.Sort;

public enum JobPostingSort {
    // 마감 임박순: closes_at ASC NULLS LAST, job_posting_id DESC
    DEADLINE(Sort.by(Sort.Order.asc("closesAt").nullsLast(), Sort.Order.desc("id"))),
    // 인기순: bookmark_count DESC, job_posting_id DESC
    POPULAR(Sort.by(Sort.Order.desc("bookmarkCount"), Sort.Order.desc("id")));

    private final Sort sort;

    JobPostingSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
