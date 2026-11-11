package com.lifelog.domain.jobposting.entity;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "job_posting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_id")
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CareerType careerType;

    private Short careerMinMonths;

    private Short careerMaxMonths;

    @Column(nullable = false, length = 30)
    private String regionCode;

    @Column(nullable = false, length = 255)
    private String workplaceAddress;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineType deadlineType;

    private LocalDateTime closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobPostingStatus status;

    @Column(nullable = false, length = 50)
    private String source;

    // 서울 일자리포털은 고유 ID가 없어 공고 내용 해시를 사용
    @Column(name = "external_id", nullable = false, length = 150)
    private String externalId;

    // 원본 링크가 없으면 null (프론트에서 일자리포털 메인으로 대체)
    @Column(length = 500)
    private String applyUrl;

    // TODO: 3단계에서 명세 기준 enum으로 변경
    @Column(length = 30)
    private String jobGroup;

    @Column(length = 50)
    private String educationRequirement;

    // 동시 요청에도 꼬이지 않도록 UPDATE 쿼리로만 증감, 엔티티 변경 감지로는 쓰지 않음
    @ColumnDefault("0")
    @Column(nullable = false, insertable = false, updatable = false)
    private int bookmarkCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @OrderBy("id ASC")
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingPay> pays = new ArrayList<>();

    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingRequirement> requirements = new ArrayList<>();

    @Builder
    private JobPosting(String title, EmploymentType employmentType, CareerType careerType,
                       Short careerMinMonths, Short careerMaxMonths,
                       String regionCode, String workplaceAddress,
                       String responsibilities, String description,
                       LocalDateTime publishedAt, DeadlineType deadlineType, LocalDateTime closesAt,
                       String source, String externalId,
                       String applyUrl, String jobGroup, String educationRequirement,
                       Company company) {
        this.title = title;
        this.employmentType = employmentType;
        this.careerType = careerType;
        this.careerMinMonths = careerMinMonths;
        this.careerMaxMonths = careerMaxMonths;
        this.regionCode = regionCode;
        this.workplaceAddress = workplaceAddress;
        this.responsibilities = responsibilities;
        this.description = description;
        this.publishedAt = publishedAt;
        this.deadlineType = deadlineType;
        this.closesAt = closesAt;
        this.status = JobPostingStatus.OPEN;
        this.source = source;
        this.externalId = externalId;
        this.applyUrl = applyUrl;
        this.jobGroup = jobGroup;
        this.educationRequirement = educationRequirement;
        this.company = company;
    }

    public void addPay(JobPostingPay pay) {
        pays.add(pay);
        pay.assignJobPosting(this);
    }

    public void addRequirement(JobPostingRequirement requirement) {
        requirements.add(requirement);
        requirement.assignJobPosting(this);
    }
}
