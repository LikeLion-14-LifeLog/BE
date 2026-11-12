package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.entity.JobPosting;

/**
 * 원본 한 건을 변환한 결과. posting은 company·직무 연결이 비어 있는 임시 객체.
 * @param jobCode 8차 고용직업분류 4자리 (job.code), 변환할 수 없으면 null
 */
public record JobPostingSyncItem(JobPosting posting, String companyName, String industryName, String jobCode) {

    public String externalId() {
        return posting.getExternalId();
    }
}
