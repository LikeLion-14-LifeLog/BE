package com.lifelog.domain.jobposting.entity;

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

import java.util.Objects;

@Getter
@Entity
@Table(name = "job_posting_pay")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingPay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_pay_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayPolicy payPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayPeriod payPeriod;

    // 회사 내규·협의면 null
    private Long amountMin;

    private Long amountMax;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Builder
    private JobPostingPay(PayStage stage, PayPolicy payPolicy, PayPeriod payPeriod,
                          Long amountMin, Long amountMax, String description) {
        this.stage = stage;
        this.payPolicy = payPolicy;
        this.payPeriod = payPeriod;
        this.amountMin = amountMin;
        this.amountMax = amountMax;
        this.description = description;
    }

    boolean hasSameValues(JobPostingPay other) {
        return stage == other.stage
                && payPolicy == other.payPolicy
                && payPeriod == other.payPeriod
                && Objects.equals(amountMin, other.amountMin)
                && Objects.equals(amountMax, other.amountMax)
                && Objects.equals(description, other.description);
    }

    void assignJobPosting(JobPosting jobPosting) {
        this.jobPosting = jobPosting;
    }
}
