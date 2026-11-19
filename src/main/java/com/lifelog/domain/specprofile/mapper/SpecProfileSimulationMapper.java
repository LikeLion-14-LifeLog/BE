package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.specprofile.dto.AcademicAbilityResDto;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.CertificateResDto;
import com.lifelog.domain.specprofile.dto.ExperienceResDto;
import com.lifelog.domain.specprofile.dto.LanguageExamScoreResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAnalysis;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationContext;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationResDto;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class SpecProfileSimulationMapper {
    /**
     * 현재 스펙을 유지한 채 목표 항목만 추가한 독립 스냅샷을 만든다.
     * @param base 현재 스펙
     * @param changes 추가 조건
     * @param majors 검증된 전공
     * @param certificates 검증된 자격증
     * @param exams 검증된 시험
     * @return 가상 항목의 DB PK와 종료·취득 연월이 없는 목표 스냅샷
     */
    public SpecProfileAnalyzationSnapshot toSimulatedSnapshot(SpecProfileAnalyzationSnapshot base,
            SpecProfileSimulationReqDto.Changes changes, Map<Long, Major> majors,
            Map<Long, Certification> certificates, Map<Long, LanguageExam> exams) {
        List<AcademicAbilityResDto> academics = changes.academicAbilities().stream().map(academic -> {
            Major major = majors.get(academic.majorId());
            return AcademicAbilityResDto.builder().type(academic.type()).status(academic.status())
                    .location(academic.location()).universityGrade(academic.universityGrade())
                    .universityStandardGrade(academic.universityStandardGrade())
                    .majorId(major.getId()).majorName(major.getName()).majorAffiliation(major.getMainField()).build();
        }).toList();
        List<CertificateResDto> addedCertificates = changes.certificates().stream().map(certificate ->
                CertificateResDto.builder().certificateId(certificate.certificateId())
                        .certificateName(certificates.get(certificate.certificateId()).getName()).build()).toList();
        List<LanguageExamScoreResDto> scores = changes.languageExamScores().stream().map(score ->
                LanguageExamScoreResDto.builder().languageExamId(score.languageExamId())
                        .languageExamName(exams.get(score.languageExamId()).getName())
                        .scoreNumeric(score.scoreNumeric()).scoreGrade(score.scoreGrade()).build()).toList();
        List<ExperienceResDto> experiences = changes.experiences().stream().map(experience ->
                ExperienceResDto.builder().type(experience.type()).title(experience.title())
                        .startedYearMonth(experience.startedYearMonth()).description(experience.description())
                        .awarded(experience.awarded()).organizerName(experience.organizerName())
                        .awardGrade(experience.awardGrade()).build()).toList();
        return SpecProfileAnalyzationSnapshot.builder().specProfileId(base.specProfileId())
                .birthYear(base.birthYear()).residenceRegion(base.residenceRegion())
                .jobId(base.jobId()).jobName(base.jobName())
                .academicAbilities(append(base.academicAbilities(), academics))
                .certificates(append(base.certificates(), addedCertificates))
                .languageExamScores(append(base.languageExamScores(), scores))
                .experiences(append(base.experiences(), experiences)).build();
    }

    /** 저장된 현재 분석을 변경 없이 비교 기준으로 사용하며 추가 준비가 없는 상태로 감싼다. */
    public SpecProfileSimulationAnalysis toStoredBaseAnalysis(SpecProfileAnnualSalaryAnalyzationResDto analysis) {
        return new SpecProfileSimulationAnalysis(analysis, "추가 준비 없음", "추가 비용 없음", "현재 보유 스펙을 기준으로 분석했어요.");
    }

    /** 현재·목표의 연차별 예측과 추가 목표의 준비 정보를 조합한다. */
    public SpecProfileSimulationResDto toResDto(Long profileId, CompanyGroupScale companyGroupScale,
            SpecProfileSimulationAnalysis base, SpecProfileSimulationAnalysis simulated) {
        return SpecProfileSimulationResDto.create(profileId, companyGroupScale,
                SpecProfileSimulationResDto.ScenarioResult.create(base.analysis().salaryResults()),
                simulated == null ? null : SpecProfileSimulationResDto.ScenarioResult.create(simulated.analysis().salaryResults()),
                simulated == null ? null : simulated.preparationPeriod(),
                simulated == null ? null : simulated.estimatedCost(),
                simulated == null ? null : simulated.decisionCriteria());
    }

    /**
     * 시뮬레이션 응답에서 기존 연봉 예측 계약을 추출한다.
     * @param ai 시뮬레이션 AI 응답
     * @return 기존 검증·연봉 매핑에 사용할 예측
     */
    public AnnualSalaryAnalyzationAiResDto toAnalyzationAiResDto(SpecProfileSimulationAiResDto ai) {
        return AnnualSalaryAnalyzationAiResDto.builder().summary(ai.summary()).salaryResults(ai.salaryResults())
                .comparisonGroupDescription(ai.comparisonGroupDescription()).recommendation(ai.recommendation()).build();
    }

    /**
     * 서버 통계가 적용된 연봉 분석과 검증된 목표 준비 정보를 결합한다.
     * @param analysis 기존 연봉 분석 응답
     * @param ai 검증된 시뮬레이션 AI 응답
     * @return 연봉 예측과 목표 준비 정보
     */
    public SpecProfileSimulationAnalysis toAnalysis(SpecProfileAnnualSalaryAnalyzationResDto analysis,
            SpecProfileSimulationAiResDto ai) {
        return new SpecProfileSimulationAnalysis(analysis, ai.preparationPeriod(), ai.estimatedCost(), ai.decisionCriteria());
    }

    /**
     * 스냅샷에 완료된 분석과 기업 규모를 결합한다.
     * @param context 현재·목표 스냅샷
     * @param companyGroupScale 예측 기업 규모
     * @param base 현재 분석 결과
     * @param simulated 목표 분석 결과
     * @param response 목표 준비 정보를 포함한 클라이언트 응답
     * @return 재사용할 완료 시뮬레이션
     */
    public SpecProfileSimulationContext toCachedContext(SpecProfileSimulationContext context,
            CompanyGroupScale companyGroupScale, SpecProfileSimulationAnalysis base,
            SpecProfileSimulationAnalysis simulated, SpecProfileSimulationResDto response) {
        return new SpecProfileSimulationContext(context.base(), context.simulated(), companyGroupScale,
                base.analysis(), simulated == null ? null : simulated.analysis(), response);
    }

    /** 원본 목록을 수정하지 않고 현재·추가 항목 순서의 불변 목록을 반환한다. */
    private <T> List<T> append(List<T> base, List<T> additions) {
        return Stream.concat(base.stream(), additions.stream()).toList();
    }
}
