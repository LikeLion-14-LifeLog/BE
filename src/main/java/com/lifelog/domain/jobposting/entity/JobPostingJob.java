package com.lifelog.domain.jobposting.entity;

import com.lifelog.domain.job.entity.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공고 ↔ 직무(8차 고용직업분류) 연결. 맞춤 공고의 희망·목표 직무 조건에 사용
@Getter
@Entity
@Table(name = "job_posting_job",
        uniqueConstraints = @UniqueConstraint(columnNames = {"job_posting_id", "job_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_job_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    JobPostingJob(JobPosting jobPosting, Job job) {
        this.jobPosting = jobPosting;
        this.job = job;
    }
}
