package com.lifelog.domain.jobposting.entity;

import com.lifelog.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_posting_requirement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingRequirement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_requirement_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementType type;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private short sortOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Builder
    private JobPostingRequirement(RequirementPriority priority, RequirementType type,
                                  String content, short sortOrder) {
        this.priority = priority;
        this.type = type;
        this.content = content;
        this.sortOrder = sortOrder;
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }
}
