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

    @Column(name = "annual", nullable = false)
    private int annual;

    @Column(name = "annual_income_min", nullable = false)
    private Long annualIncomeMin;

    @Column(name = "annual_income_expected", nullable = false)
    private Long annualIncomeExpected;

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

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "annual_salary_min_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryMinKrw;

    @Column(name = "annual_salary_max_krw", precision = 19, scale = 2)
    private BigDecimal annualSalaryMaxKrw;

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
