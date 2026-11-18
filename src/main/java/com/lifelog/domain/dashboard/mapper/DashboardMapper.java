package com.lifelog.domain.dashboard.mapper;

import com.lifelog.domain.dashboard.dto.DashboardResDto;
import com.lifelog.domain.dashboard.enums.DashboardCoreSpecType;
import com.lifelog.domain.jobposting.dto.JobPostingCardResponse;
import com.lifelog.domain.jobposting.dto.JobPostingListResponse;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingRequirement;
import com.lifelog.domain.jobposting.entity.RequirementType;
import com.lifelog.domain.specprofile.dto.AcademicAbilityResDto;
import com.lifelog.domain.specprofile.dto.CertificateResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DashboardMapper {
    /** 실행 메타데이터와 기존 조회가 검증한 저장 보고서를 대시보드 분석 정보로 변환한다. */
    public DashboardResDto.Analysis toAnalysisResDto(SpecProfileAnalyzationRun run,
            SpecProfileAnalyzationRunResultResDto runResult) {
        DashboardResDto.Result result = null;
        SpecProfileAnnualSalaryAnalyzationResDto report = runResult.result();
        if (report != null) {
            SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult salary = report.salaryResults().stream()
                    .filter(value -> value.annual() == 0).findFirst()
                    .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.INCOMPLETE_SAVED_RESULT));
            result = DashboardResDto.Result.builder().annual(0).expectedStartingSalary(salary.annualIncomeExpected())
                    .topPercent(salary.topPercent()).salaryNotice(report.salaryNotice())
                    .coreSpecs(toCoreSpecs(report.appliedSpecs())).build();
        }
        return DashboardResDto.Analysis.builder().runId(run.getId()).specProfileId(runResult.specProfileId())
                .status(runResult.status()).requestedAt(run.getCreatedAt().atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()).result(result).build();
    }

    /** 스냅샷의 대표 학력·동일 학점·첫 자격증·희망 직무에서 값이 있는 칩만 구성한다. */
    public List<DashboardResDto.CoreSpec> toCoreSpecs(SpecProfileAnnualSalaryAnalyzationResDto.AppliedSpecs specs) {
        List<DashboardResDto.CoreSpec> chips = new ArrayList<>();
        AcademicAbilityResDto academic = specs.academicAbilities().stream()
                .filter(value -> value != null && value.type() != null)
                .min(Comparator.comparingInt(value -> educationOrder(value.type()))).orElse(null);
        if (academic != null) {
            String label = educationLabel(academic.type());
            if (academic.majorName() != null && !academic.majorName().isBlank()) label += " · " + academic.majorName().strip();
            chips.add(DashboardResDto.CoreSpec.builder().type(DashboardCoreSpecType.EDUCATION).label(label).build());
            if (academic.universityGrade() != null && academic.universityStandardGrade() != null) {
                chips.add(DashboardResDto.CoreSpec.builder().type(DashboardCoreSpecType.GPA)
                        .label("학점 " + academic.universityGrade().stripTrailingZeros().toPlainString()
                                + " / " + academic.universityStandardGrade().stripTrailingZeros().toPlainString()).build());
            }
        }
        specs.certificates().stream().filter(Objects::nonNull).map(CertificateResDto::certificateName)
                .filter(value -> value != null && !value.isBlank()).findFirst()
                .ifPresent(name -> chips.add(DashboardResDto.CoreSpec.builder().type(DashboardCoreSpecType.CERTIFICATE)
                        .label(name.strip() + " 취득").build()));
        if (specs.jobName() != null && !specs.jobName().isBlank()) {
            chips.add(DashboardResDto.CoreSpec.builder().type(DashboardCoreSpecType.DESIRED_JOB)
                    .label("희망 직무 " + specs.jobName().strip()).build());
        }
        return List.copyOf(chips);
    }

    /** 기존 카드의 값과 순서를 보존하면서 저장된 마감 유형·기술 요건만 덧붙인다. */
    public DashboardResDto.JobPostingCard toJobPostingResDto(JobPostingCardResponse card, JobPosting posting) {
        if (posting == null) throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        List<String> tags = posting.getRequirements().stream().filter(value -> value.getType() == RequirementType.SKILL)
                .sorted(Comparator.comparingInt(JobPostingRequirement::getSortOrder)
                        .thenComparing(JobPostingRequirement::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(JobPostingRequirement::getContent).filter(Objects::nonNull).map(String::strip)
                .filter(value -> !value.isEmpty()).distinct().toList();
        return DashboardResDto.JobPostingCard.builder().jobPostingId(card.jobPostingId()).title(card.title())
                .companyName(card.companyName()).workRegion(card.workRegion()).expectedSalaryMin(card.expectedSalaryMin())
                .expectedSalaryMax(card.expectedSalaryMax()).deadlineType(posting.getDeadlineType()).dDay(card.dDay())
                .skillTags(tags).build();
    }

    /** 분석 정보와 기존 맞춤 공고의 전체 건수·카드를 대시보드 전용 성공 응답으로 조합한다. */
    public DashboardResDto toDashboardResDto(DashboardResDto.Analysis analysis, JobPostingListResponse postings,
            Map<Long, JobPosting> supplements) {
        List<DashboardResDto.JobPostingCard> cards = postings.jobPostings().stream()
                .map(card -> toJobPostingResDto(card, supplements.get(card.jobPostingId()))).toList();
        return DashboardResDto.builder().status(200).message("요청이 성공적으로 처리되었습니다.")
                .data(DashboardResDto.Data.builder().analysis(analysis)
                        .recommendedJobPostings(DashboardResDto.RecommendedJobPostings.builder()
                                .personalized(postings.personalized()).totalCount(postings.tabCounts().recommended())
                                .jobPostings(cards).build()).build()).build();
    }

    /** 같은 단계에서는 스냅샷 순서를 유지하도록 대표 학력의 우선순위만 반환한다. */
    private int educationOrder(AcademicAbilityType type) {
        return switch (type) {
            case GRADUATE_SCHOOL_DOCTORAL -> 0;
            case GRADUATE_SCHOOL_MASTER, GRADUATE_SCHOOL -> 1;
            case UNIVERSITY -> 2;
            case COLLEGE -> 3;
            case HIGH_SCHOOL -> 4;
            case ETC -> 5;
        };
    }

    /** 학교명을 생성하지 않고 저장된 학력 유형을 표시 문구로 변환한다. */
    private String educationLabel(AcademicAbilityType type) {
        return switch (type) {
            case GRADUATE_SCHOOL_DOCTORAL -> "대학원 박사";
            case GRADUATE_SCHOOL_MASTER -> "대학원 석사";
            case GRADUATE_SCHOOL -> "대학원";
            case UNIVERSITY -> "대학교";
            case COLLEGE -> "전문대";
            case HIGH_SCHOOL -> "고등학교";
            case ETC -> "기타 학력";
        };
    }
}
