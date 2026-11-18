package com.lifelog.domain.specprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "spec_profile_analyzation_result", uniqueConstraints =
        @UniqueConstraint(columnNames = {"spec_profile_analyzation_run_id", "annual"}))
public class SpecProfileAnalyzationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long id;

    /** AI 예측 대상 연차. 0은 첫 취업, 3과 5는 취업 후 해당 연차를 의미한다. */
    @Column(name = "annual", nullable = false)
    private int annual;

    /** AI가 예측한 사용자의 해당 연차 연봉 하한(원). 통계적 신뢰구간의 하한은 아니다. */
    @Column(name = "annual_income_min", nullable = false)
    private Long annualIncomeMin;

    /** AI가 예측한 사용자의 해당 연차 대표 예상 연봉(원). 비교 표본의 산술평균과 구분한다. */
    @Column(name = "annual_income_expected", nullable = false)
    private Long annualIncomeExpected;

    /** AI가 예측한 사용자의 해당 연차 연봉 상한(원). 통계적 신뢰구간의 상한은 아니다. */
    @Column(name = "annual_income_max", nullable = false)
    private Long annualIncomeMax;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "comparison_group_description", columnDefinition = "TEXT")
    private String comparisonGroupDescription;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "top_percent", precision = 4, scale = 1)
    private BigDecimal topPercent;

    @Column(name = "job_sub_category_code")
    private String jobSubCategoryCode;

    @Column(name = "job_sub_category_name")
    private String jobSubCategoryName;

    /** 서버가 통계 계산에 사용한 고유 비교 표본 인원수. 사람당 관측 연도 하나를 선택하며 분석의 모든 연차에 같은 값을 저장한다. */
    @Column(name = "sample_count")
    private Integer sampleCount;

    /** 서버가 계산한 비교 표본의 실제 연환산 소득 최솟값(원). 분석 공통 통계로, 해당 연차의 AI 예측 하한과 구분한다. */
    @Column(name = "annual_salary_min_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryMinKrw;

    /** 서버가 계산한 비교 표본의 실제 연환산 소득 최댓값(원). 분석 공통 통계로, 해당 연차의 AI 예측 상한과 구분한다. */
    @Column(name = "annual_salary_max_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryMaxKrw;

    /** 서버가 계산한 비교 표본의 실제 연환산 소득 산술평균(원). 분석 공통 통계이며 소수 둘째 자리까지 반올림한다. */
    @Column(name = "annual_salary_mean_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryMeanKrw;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "spec_profile_analyzation_result_salary_mode", joinColumns = @JoinColumn(name = "result_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "annual_salary_krw", nullable = false, precision = 19, scale = 2)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<BigDecimal> annualSalaryModesKrw = new ArrayList<>();

    @Column(name = "mode_frequency")
    private Integer modeFrequency;

    @Column(name = "annual_salary_p25_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryP25Krw;

    @Column(name = "annual_salary_median_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryMedianKrw;

    @Column(name = "annual_salary_p75_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryP75Krw;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "spec_profile_analyzation_result_observation_year", joinColumns = @JoinColumn(name = "result_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "observation_year", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<Integer> observationYears = new ArrayList<>();

    @Column(name = "salary_notice", columnDefinition = "TEXT")
    private String salaryNotice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_profile_analyzation_run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SpecProfileAnalyzationRun run;
}
