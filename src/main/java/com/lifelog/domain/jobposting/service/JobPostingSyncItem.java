package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.entity.JobPosting;

// 원본 한 건을 변환한 결과. posting은 company가 비어 있는 임시 객체
public record JobPostingSyncItem(JobPosting posting, String companyName, String industryName) {

    public String externalId() {
        return posting.getExternalId();
    }
}
