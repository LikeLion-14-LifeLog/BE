package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SpecProfileTargetMapper {
    private final JsonMapper jsonMapper;
    private final SpecProfileMapper specProfileMapper;

    /** 선택된 TARGET의 추가 항목을 저장된 PK·참조 이름·연월과 함께 변환한다. */
    public SpecProfileTargetResDto toSelectedTargetResDto(SpecProfile target, List<AcademicAbility> academics,
            Map<Long, Major> majors, List<SpecProfileCertificate> certificates, List<LanguageExamScore> scores,
            List<Experience> experiences) {
        return SpecProfileTargetResDto.builder().targetSpecProfileId(target.getId())
                .baseSpecProfileId(target.getBaseSpecProfile().getId()).companyGroupScale(target.getTargetCompanyGroupScale())
                .changes(SpecProfileTargetResDto.Changes.builder()
                        .academicAbilities(academics.stream().map(value -> specProfileMapper.toAcademicAbilityResDto(value, majors.get(value.getId()))).toList())
                        .certificates(certificates.stream().map(specProfileMapper::toCertificateResDto).toList())
                        .languageExamScores(scores.stream().map(specProfileMapper::toLanguageExamScoreResDto).toList())
                        .experiences(experiences.stream().map(this::toTargetExperienceResDto).toList()).build()).build();
    }

    /** 미수상 경험은 수상 상세를 노출하지 않으며 저장된 날짜만 반환한다. */
    private ExperienceResDto toTargetExperienceResDto(Experience experience) {
        ExperienceResDto result = specProfileMapper.toExperienceResDto(experience);
        if (result.awarded()) return result;
        return ExperienceResDto.builder().experienceId(result.experienceId()).type(result.type()).title(result.title())
                .startedYearMonth(result.startedYearMonth()).endedYearMonth(result.endedYearMonth())
                .description(result.description()).awarded(false).organizerName(null).awardGrade(null).build();
    }

    /** 본인 CURRENT에서 사용자 TARGET의 기본 정보를 복제한다. */
    public SpecProfile toUserTarget(SpecProfile base) {
        SpecProfile target = SpecProfile.builder().member(base.getMember()).build();
        target.applyUserTarget(base);
        return target;
    }

    /** 추천에 허용할 마스터 엔티티를 최소 정보의 참조 DTO로 변환한다. */
    public SpecProfileTargetReferenceResDto toReferences(Collection<Major> majors,
            Collection<Certification> certificates, Collection<LanguageExam> exams) {
        return SpecProfileTargetReferenceResDto.builder()
                .majors(majors.stream().map(value -> SpecProfileTargetReferenceResDto.MajorReference.builder()
                        .id(value.getId()).name(value.getName()).mainField(value.getMainField()).degreeProcess(value.getDegreeProcess()).build()).toList())
                .certificates(certificates.stream().map(value -> SpecProfileTargetReferenceResDto.CertificateReference.builder()
                        .id(value.getId()).name(value.getName()).build()).toList())
                .languageExams(exams.stream().map(value -> SpecProfileTargetReferenceResDto.LanguageExamReference.builder()
                        .id(value.getId()).name(value.getName()).scoreType(value.getScoreType()).build()).toList()).build();
    }

    /** 저장용 날짜를 제외하고 기존 시뮬레이터와 같은 분석 조건으로 투영한다. */
    public SpecProfileSimulationReqDto.Changes toSimulationChanges(SpecProfileTargetChanges changes) {
        return new SpecProfileSimulationReqDto.Changes(
                changes.academicAbilities().stream().map(value -> new SpecProfileSimulationReqDto.AcademicAbilityChange(
                        value.type(), value.status(), value.location(), value.universityGrade(),
                        value.universityStandardGrade(), value.majorAffiliation(), value.majorId())).toList(),
                changes.certificates().stream().map(value ->
                        new SpecProfileSimulationReqDto.CertificateChange(value.certificateId())).toList(),
                changes.languageExamScores().stream().map(value -> new SpecProfileSimulationReqDto.LanguageExamScoreChange(
                        value.languageExamId(), value.scoreNumeric(), value.scoreGrade())).toList(),
                changes.experiences().stream().map(value -> new SpecProfileSimulationReqDto.ExperienceChange(
                        value.type(), value.title(), value.startedYearMonth(), value.description(), value.awarded(),
                        value.organizerName(), value.awardGrade())).toList());
    }

    /** 사용자 목표를 기존 시뮬레이션 입력으로 변환한다. */
    public SpecProfileSimulationReqDto toSimulationRequest(SpecProfileTargetSaveReqDto request) {
        return new SpecProfileSimulationReqDto(request.baseSpecProfileId(), request.companyGroupScale(),
                toSimulationChanges(request.changes()));
    }

    /** 캐시의 보고서와 응답에 분리된 준비 정보를 하나의 분석 DTO로 복원한다. */
    public SpecProfileSimulationAnalysis toCachedUserAnalysis(SpecProfileSimulationContext cached) {
        return new SpecProfileSimulationAnalysis(cached.simulatedAnalysis(), cached.response().preparationPeriod(),
                cached.response().estimatedCost(), cached.response().decisionCriteria());
    }

    /** 숫자 표현과 목록 순서에 영향을 받지 않는 비교 키를 만든다. */
    public String comparisonKey(Object value) {
        return canonical(jsonMapper.valueToTree(value));
    }

    /** 분석에서 사용하지 않는 날짜를 제외한 추가 스펙의 동등성을 비교한다. */
    public boolean sameChanges(SpecProfileTargetChanges first, SpecProfileTargetChanges second) {
        return comparisonKey(toSimulationChanges(first)).equals(comparisonKey(toSimulationChanges(second)));
    }

    /**
     * 추천 스펙과 해당 슬롯의 검증된 일괄 분석 결과를 결합한다.
     * @param scenario 추천 스펙과 제목
     * @param analysis 검증된 예측·준비 정보
     * @return 분석 결과를 포함한 비교 시나리오
     */
    public SpecProfileTargetScenarioDto toAnalyzedScenario(SpecProfileTargetScenarioDto scenario,
            SpecProfileSimulationAnalysis analysis) {
        return SpecProfileTargetScenarioDto.builder().title(scenario.title()).changes(scenario.changes())
                .snapshot(scenario.snapshot()).analysis(analysis).build();
    }

    /** JSON 객체 키·목록 순서와 소수의 불필요한 0을 정규화한다. */
    private String canonical(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + canonical(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> values.add(canonical(value)));
            return values.stream().sorted().collect(Collectors.joining(",", "[", "]"));
        }
        if (node.isNumber()) return new BigDecimal(node.asString()).stripTrailingZeros().toPlainString();
        return node.toString();
    }

    /** 현재 학력과 사용자 목표 학력을 TARGET 소유의 신규 저장 요청으로 복제한다. */
    public List<AcademicAbilitySaveReqDto> toAcademicRequests(SpecProfileAnalyzationSnapshot base, SpecProfileTargetChanges changes) {
        return Stream.concat(base.academicAbilities().stream().map(value -> new AcademicAbilitySaveReqDto(null,
                        value.type(), value.status(), value.location(), value.universityGrade(), value.universityStandardGrade(),
                        value.graduatedYearMonth(), value.majorAffiliation(), value.majorId())),
                changes.academicAbilities().stream().map(value -> new AcademicAbilitySaveReqDto(null,
                        value.type(), value.status(), value.location(), value.universityGrade(), value.universityStandardGrade(),
                        value.graduatedYearMonth(), value.majorAffiliation(), value.majorId()))).toList();
    }

    /** 현재 자격증과 사용자 추가 자격증을 취득월과 함께 복제한다. */
    public List<CertificateSaveReqDto> toCertificateRequests(SpecProfileAnalyzationSnapshot base, SpecProfileTargetChanges changes) {
        return Stream.concat(base.certificates().stream().map(value -> new CertificateSaveReqDto(null,
                        value.certificateId(), value.acquiredYearMonth())),
                changes.certificates().stream().map(value -> new CertificateSaveReqDto(null,
                        value.certificateId(), value.acquiredYearMonth()))).toList();
    }

    /** 현재 어학 성적과 사용자 추가 성적을 취득·만료월과 함께 복제한다. */
    public List<LanguageExamScoreSaveReqDto> toLanguageRequests(SpecProfileAnalyzationSnapshot base, SpecProfileTargetChanges changes) {
        return Stream.concat(base.languageExamScores().stream().map(value -> new LanguageExamScoreSaveReqDto(null,
                        value.languageExamId(), value.scoreNumeric(), value.scoreGrade(), value.acquiredYearMonth(), value.expiresYearMonth())),
                changes.languageExamScores().stream().map(value -> new LanguageExamScoreSaveReqDto(null,
                        value.languageExamId(), value.scoreNumeric(), value.scoreGrade(), value.acquiredYearMonth(), value.expiresYearMonth()))).toList();
    }

    /** 현재 경험과 사용자 목표 경험을 종료월을 보존해 복제한다. */
    public List<ExperienceSaveReqDto> toExperienceRequests(SpecProfileAnalyzationSnapshot base, SpecProfileTargetChanges changes) {
        return Stream.concat(base.experiences().stream().map(value -> new ExperienceSaveReqDto(null,
                        value.type(), value.title(), value.startedYearMonth(), value.endedYearMonth(), value.description(),
                        value.awarded(), value.organizerName(), value.awardGrade())),
                changes.experiences().stream().map(value -> new ExperienceSaveReqDto(null,
                        value.type(), value.title(), value.startedYearMonth(), value.endedYearMonth(), value.description(),
                        value.awarded(), value.organizerName(), value.awardGrade()))).toList();
    }

    /** 입력한 추가 스펙의 이름으로 사용자 슬롯 제목을 구성한다. */
    public String toUserTitle(SpecProfileAnalyzationSnapshot base, SpecProfileAnalyzationSnapshot target, SpecProfileTargetChanges changes) {
        List<String> titles = new ArrayList<>();
        changes.experiences().forEach(value -> titles.add(value.title()));
        target.certificates().stream().skip(base.certificates().size()).forEach(value -> titles.add(value.certificateName()));
        target.languageExamScores().stream().skip(base.languageExamScores().size()).forEach(value -> titles.add(value.languageExamName()));
        target.academicAbilities().stream().skip(base.academicAbilities().size()).forEach(value -> titles.add(value.majorName() + " 학력 목표"));
        String title = titles.isEmpty() ? "현재 스펙 유지" : titles.stream().limit(3).collect(Collectors.joining(" · "));
        return title.length() > 255 ? title.substring(0, 255) : title;
    }

    /** 분석된 세 선택지와 저장한 사용자 TARGET ID를 화면 응답으로 조합한다. */
    public SpecProfileTargetSaveResDto toResponse(SpecProfileTargetSaveReqDto request, Long targetId,
            SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis, SpecProfileTargetScenarioDto user,
            List<SpecProfileTargetScenarioDto> alternatives) {
        long baseline = startingSalary(baseAnalysis);
        List<SpecProfileTargetSaveResDto.Option> options = new ArrayList<>();
        options.add(toOption(1, targetId, user, baseline));
        for (int index = 0; index < alternatives.size(); index++) {
            options.add(toOption(index + 2, null, alternatives.get(index), baseline));
        }
        return SpecProfileTargetSaveResDto.builder().baseSpecProfileId(request.baseSpecProfileId())
                .companyGroupScale(request.companyGroupScale()).currency("KRW").baseExpectedStartingSalary(baseline)
                .selectedTargetSpecProfileId(targetId).options(List.copyOf(options)).salaryNotice(baseAnalysis.salaryNotice())
                .preparationNotice("준비 기간·비용·활용도는 AI 참고 추정치로 개인 상황에 따라 달라질 수 있어요.").build();
    }

    /** 최종 재분석 결과만 사용하며 AI 대안의 DB 식별자는 null로 반환한다. */
    private SpecProfileTargetSaveResDto.Option toOption(int slot, Long targetId, SpecProfileTargetScenarioDto scenario, long baseline) {
        long salary = startingSalary(scenario.analysis().analysis());
        return SpecProfileTargetSaveResDto.Option.builder().slotNumber(slot).targetSpecProfileId(targetId)
                .source(slot == 1 ? SpecProfileTargetSaveResDto.Source.USER_INPUT : SpecProfileTargetSaveResDto.Source.AI_RECOMMENDATION)
                .selected(slot == 1).title(scenario.title()).changes(scenario.changes()).expectedStartingSalary(salary)
                .salaryIncreaseAmount(salary - baseline).preparationPeriod(scenario.analysis().preparationPeriod())
                .estimatedCost(scenario.analysis().estimatedCost()).decisionCriteria(scenario.analysis().decisionCriteria()).build();
    }

    /** 첫 취업 연차의 예상 연봉을 선택한다. */
    public long startingSalary(SpecProfileAnnualSalaryAnalyzationResDto report) {
        return report.salaryResults().stream().filter(value -> value.annual() == 0).findFirst()
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.INVALID_AI_RESPONSE)).annualIncomeExpected();
    }
}
