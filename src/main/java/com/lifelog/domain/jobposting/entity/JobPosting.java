package com.lifelog.domain.jobposting.entity;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.job.entity.Job;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

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

    // 카드 표시용 근무 지역명 (예: "서울 강남구")
    @Column(length = 50)
    private String regionName;

    @Column(length = 50)
    private String educationRequirement;

    // 최소 학력 단계: 0 학력무관, 1 초졸이하, 2 중졸, 3 고졸, 4 대졸(2~3년), 5 대졸(4년), 6 석사, 7 박사
    @Column(nullable = false)
    private short educationLevel;

    // 원본 모집직종코드 (서울 일자리포털 JOBS_CD, 고용24 6자리)
    @Column(length = 10)
    private String externalJobCode;

    @Column(length = 255)
    private String workSchedule;

    // 4대보험·퇴직금·기타 복리후생을 줄바꿈으로 합친 값
    @Column(columnDefinition = "TEXT")
    private String benefits;

    // 전형 방법·접수 방법·제출 서류를 줄바꿈으로 합친 값
    @Column(columnDefinition = "TEXT")
    private String applicationMethod;

    // 동시 요청에도 꼬이지 않도록 UPDATE 쿼리로만 증감, 엔티티 변경 감지로는 쓰지 않음
    @ColumnDefault("0")
    @Column(nullable = false, insertable = false, updatable = false)
    private int bookmarkCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 수집 시 여러 공고의 하위 데이터를 IN 쿼리로 한 번에 읽도록 BatchSize 지정
    @BatchSize(size = 1000)
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingPay> pays = new ArrayList<>();

    @BatchSize(size = 1000)
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingRequirement> requirements = new ArrayList<>();

    @BatchSize(size = 1000)
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingJob> jobs = new ArrayList<>();

    @Builder
    private JobPosting(String title, EmploymentType employmentType, CareerType careerType,
                       Short careerMinMonths, Short careerMaxMonths,
                       String regionCode, String workplaceAddress,
                       String responsibilities, String description,
                       LocalDateTime publishedAt, DeadlineType deadlineType, LocalDateTime closesAt,
                       JobPostingStatus status, String source, String externalId,
                       String applyUrl, String regionName,
                       String educationRequirement, short educationLevel,
                       String externalJobCode, String workSchedule,
                       String benefits, String applicationMethod,
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
        this.status = status == null ? JobPostingStatus.OPEN : status;
        this.source = source;
        this.externalId = externalId;
        this.applyUrl = applyUrl;
        this.regionName = regionName;
        this.educationRequirement = educationRequirement;
        this.educationLevel = educationLevel;
        this.externalJobCode = externalJobCode;
        this.workSchedule = workSchedule;
        this.benefits = benefits;
        this.applicationMethod = applicationMethod;
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

    public void assignCompany(Company company) {
        this.company = company;
    }

    // 연결된 직무가 정확히 이 직무 하나인지 (job이 null이면 연결이 없어야 같음)
    public boolean hasOnlyJob(Job job) {
        if (job == null) {
            return jobs.isEmpty();
        }
        return jobs.size() == 1 && Objects.equals(jobs.getFirst().getJob().getId(), job.getId());
    }

    // 공고 하나에 직무 하나. null이면 연결 해제
    public void replaceJob(Job job) {
        if (hasOnlyJob(job)) {
            return;
        }
        jobs.clear();
        if (job != null) {
            jobs.add(new JobPostingJob(this, job));
        }
    }

    public boolean isOpen() {
        return status == JobPostingStatus.OPEN;
    }

    // 마감까지 남은 일수 (마감일 당일 0, 지났으면 음수, 상시 채용이면 null)
    public Integer daysUntilClose(LocalDate today) {
        return closesAt == null ? null : (int) ChronoUnit.DAYS.between(today, closesAt.toLocalDate());
    }

    // source·externalId·company·저장 수를 제외한 공고 내용과 하위 급여·자격요건 비교
    public boolean hasSameValues(JobPosting other) {
        return Objects.equals(title, other.title)
                && employmentType == other.employmentType
                && careerType == other.careerType
                && Objects.equals(careerMinMonths, other.careerMinMonths)
                && Objects.equals(careerMaxMonths, other.careerMaxMonths)
                && Objects.equals(regionCode, other.regionCode)
                && Objects.equals(workplaceAddress, other.workplaceAddress)
                && Objects.equals(responsibilities, other.responsibilities)
                && Objects.equals(description, other.description)
                && Objects.equals(publishedAt, other.publishedAt)
                && deadlineType == other.deadlineType
                && Objects.equals(closesAt, other.closesAt)
                && status == other.status
                && Objects.equals(applyUrl, other.applyUrl)
                && Objects.equals(regionName, other.regionName)
                && Objects.equals(educationRequirement, other.educationRequirement)
                && educationLevel == other.educationLevel
                && Objects.equals(externalJobCode, other.externalJobCode)
                && Objects.equals(workSchedule, other.workSchedule)
                && Objects.equals(benefits, other.benefits)
                && Objects.equals(applicationMethod, other.applicationMethod)
                && sameElements(pays, other.pays, JobPostingPay::hasSameValues)
                && sameElements(requirements, other.requirements, JobPostingRequirement::hasSameValues);
    }

    // id·source·externalId·저장 수는 유지하고 나머지를 원본 값으로 교체 (source는 버려지는 임시 객체)
    public void update(JobPosting source, Company company) {
        this.title = source.title;
        this.employmentType = source.employmentType;
        this.careerType = source.careerType;
        this.careerMinMonths = source.careerMinMonths;
        this.careerMaxMonths = source.careerMaxMonths;
        this.regionCode = source.regionCode;
        this.workplaceAddress = source.workplaceAddress;
        this.responsibilities = source.responsibilities;
        this.description = source.description;
        this.publishedAt = source.publishedAt;
        this.deadlineType = source.deadlineType;
        this.closesAt = source.closesAt;
        this.status = source.status;
        this.applyUrl = source.applyUrl;
        this.regionName = source.regionName;
        this.educationRequirement = source.educationRequirement;
        this.educationLevel = source.educationLevel;
        this.externalJobCode = source.externalJobCode;
        this.workSchedule = source.workSchedule;
        this.benefits = source.benefits;
        this.applicationMethod = source.applicationMethod;
        this.company = company;

        pays.clear();
        List.copyOf(source.pays).forEach(this::addPay);
        requirements.clear();
        List.copyOf(source.requirements).forEach(this::addRequirement);
    }

    private static <T> boolean sameElements(List<T> a, List<T> b, BiPredicate<T, T> same) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!same.test(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
