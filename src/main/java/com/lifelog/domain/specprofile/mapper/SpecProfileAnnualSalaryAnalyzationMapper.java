package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto.SalaryPrediction;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto.AppliedSpecs;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto.ComparisonGroup;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationResult;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SpecProfileAnnualSalaryAnalyzationMapper {
    private static final String SALARY_NOTICE = "한국교육고용패널Ⅱ(KEEPⅡ) 비교표본 기반 · 금액은 원화 연환산 · 연차별 연봉과 음영 범위는 AI 추정값 · 상위 비율은 비교 표본 내 순위";

    /**
     * 검증된 AI 예측에 서버가 보유한 통계, 비교 집단, 요청 시점 스펙을 조합한다.
     * @param ai 검증된 예측
     * @param snapshot 요청 시점 스펙
     * @param conditions 표본 필터링 조건
     * @param samples 선택된 비교 표본
     * @param statistics 서버 계산 관측 통계
     * @param topPercent 첫 취업 예측값의 비교 표본 내 상위 비율
     * @return 연차 순으로 정렬된 분석 응답
     */
    public SpecProfileAnnualSalaryAnalyzationResDto toResDto(AnnualSalaryAnalyzationAiResDto ai,
            SpecProfileAnalyzationSnapshot snapshot, SpecProfileSalaryClassificationData conditions,
            List<KeepSalaryMatchedSample> samples, KeepSalaryMatchedSampleStatistics statistics, BigDecimal topPercent) {
        List<SalaryResult> predictions = ai.salaryResults().stream().sorted(Comparator.comparing(SalaryPrediction::annual))
                .map(prediction -> SalaryResult.builder().annual(prediction.annual())
                        .annualIncomeMin(prediction.annualIncomeMin()).annualIncomeExpected(prediction.annualIncomeExpected())
                        .annualIncomeMax(prediction.annualIncomeMax()).topPercent(prediction.annual() == 0 ? topPercent : null).build())
                .toList();
        ComparisonGroup group = ComparisonGroup.builder().jobSubCategoryCode(conditions.jobSubCategoryCode())
                .jobSubCategoryName(conditions.jobSubCategoryName()).sampleCount(statistics.sampleCount())
                .observationYears(samples.stream().map(KeepSalaryMatchedSample::selectedYear).distinct().sorted().toList())
                .description(ai.comparisonGroupDescription()).build();
        return SpecProfileAnnualSalaryAnalyzationResDto.builder().summary(ai.summary()).salaryResults(predictions)
                .comparisonGroup(group).appliedSpecs(toAppliedSpecs(snapshot)).statistics(statistics)
                .recommendation(ai.recommendation()).salaryNotice(SALARY_NOTICE).build();
    }

    /**
     * 저장된 관측 통계·표시 문구와 요청 당시 스냅샷으로 보고서를 복원한다.
     * @param results 검증된 연차 오름차순 결과
     * @param snapshot 요청 당시 스냅샷
     * @return 재분석 없이 복원한 보고서
     */
    public SpecProfileAnnualSalaryAnalyzationResDto toResDto(List<SpecProfileAnalyzationResult> results,
                                                           SpecProfileAnalyzationSnapshot snapshot) {
        SpecProfileAnalyzationResult common = results.getFirst();
        List<SalaryResult> predictions = results.stream().map(result -> SalaryResult.builder()
                .annual(result.getAnnual()).annualIncomeMin(result.getAnnualIncomeMin())
                .annualIncomeExpected(result.getAnnualIncomeExpected()).annualIncomeMax(result.getAnnualIncomeMax())
                .topPercent(result.getAnnual() == 0 ? result.getTopPercent() : null).build()).toList();
        ComparisonGroup group = ComparisonGroup.builder().jobSubCategoryCode(common.getJobSubCategoryCode())
                .jobSubCategoryName(common.getJobSubCategoryName()).sampleCount(common.getSampleCount())
                .observationYears(copy(common.getObservationYears())).description(common.getComparisonGroupDescription()).build();
        KeepSalaryMatchedSampleStatistics statistics = KeepSalaryMatchedSampleStatistics.builder()
                .sampleCount(common.getSampleCount()).annualSalaryMinKrw(common.getAnnualSalaryMinKrw())
                .annualSalaryMaxKrw(common.getAnnualSalaryMaxKrw()).annualSalaryMeanKrw(common.getAnnualSalaryMeanKrw())
                .annualSalaryModesKrw(copy(common.getAnnualSalaryModesKrw())).modeFrequency(common.getModeFrequency())
                .annualSalaryP25Krw(common.getAnnualSalaryP25Krw()).annualSalaryMedianKrw(common.getAnnualSalaryMedianKrw())
                .annualSalaryP75Krw(common.getAnnualSalaryP75Krw()).build();
        return SpecProfileAnnualSalaryAnalyzationResDto.builder().summary(common.getSummary()).salaryResults(predictions)
                .comparisonGroup(group).appliedSpecs(toAppliedSpecs(snapshot)).statistics(statistics)
                .recommendation(common.getRecommendation()).salaryNotice(common.getSalaryNotice()).build();
    }

    /**
     * 실행 정보에 완료된 보고서 또는 null을 조합한다.
     * @param run 조회한 실행
     * @param report 완료된 보고서 또는 null
     * @return 실행 상태 및 결과 응답
     */
    public SpecProfileAnalyzationRunResultResDto toRunResultResDto(SpecProfileAnalyzationRun run,
                                                                 SpecProfileAnnualSalaryAnalyzationResDto report) {
        return SpecProfileAnalyzationRunResultResDto.builder().runId(run.getId()).specProfileId(run.getSpecProfile().getId())
                .status(run.getStatus()).result(report).build();
    }

    /**
     * 요청 당시 스펙을 목록 누락 시 빈 목록으로 정규화해 복사한다.
     * @param snapshot 요청 당시 스냅샷
     * @return 분석에 반영한 스펙
     */
    private AppliedSpecs toAppliedSpecs(SpecProfileAnalyzationSnapshot snapshot) {
        return AppliedSpecs.builder().birthYear(snapshot.birthYear()).residenceRegion(snapshot.residenceRegion())
                .jobId(snapshot.jobId()).jobName(snapshot.jobName()).academicAbilities(copy(snapshot.academicAbilities()))
                .certificates(copy(snapshot.certificates())).languageExamScores(copy(snapshot.languageExamScores()))
                .experiences(copy(snapshot.experiences())).build();
    }

    /**
     * 연차별 예측, 진단 문구와 분석 당시의 비교 집단·관측 통계를 결과 Entity로 변환한다.
     * @param run 저장할 실행
     * @param report 전체 분석 보고서
     * @return 연차별 결과 Entity 목록
     */
    public List<SpecProfileAnalyzationResult> toResults(SpecProfileAnalyzationRun run,
                                                       SpecProfileAnnualSalaryAnalyzationResDto report) {
        return report.salaryResults().stream().map(result -> SpecProfileAnalyzationResult.builder()
                .run(run).annual(result.annual()).annualIncomeMin(result.annualIncomeMin())
                .annualIncomeExpected(result.annualIncomeExpected()).annualIncomeMax(result.annualIncomeMax())
                .summary(report.summary()).comparisonGroupDescription(report.comparisonGroup().description())
                .recommendation(report.recommendation()).topPercent(result.topPercent())
                .jobSubCategoryCode(report.comparisonGroup().jobSubCategoryCode())
                .jobSubCategoryName(report.comparisonGroup().jobSubCategoryName())
                .observationYears(new ArrayList<>(report.comparisonGroup().observationYears()))
                .sampleCount(report.statistics().sampleCount())
                .annualSalaryMinKrw(report.statistics().annualSalaryMinKrw())
                .annualSalaryMaxKrw(report.statistics().annualSalaryMaxKrw())
                .annualSalaryMeanKrw(report.statistics().annualSalaryMeanKrw())
                .annualSalaryModesKrw(new ArrayList<>(report.statistics().annualSalaryModesKrw()))
                .modeFrequency(report.statistics().modeFrequency())
                .annualSalaryP25Krw(report.statistics().annualSalaryP25Krw())
                .annualSalaryMedianKrw(report.statistics().annualSalaryMedianKrw())
                .annualSalaryP75Krw(report.statistics().annualSalaryP75Krw())
                .salaryNotice(report.salaryNotice()).build()).toList();
    }

    /** 누락된 선택 항목 목록을 빈 불변 목록으로 변환한다. */
    private <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
